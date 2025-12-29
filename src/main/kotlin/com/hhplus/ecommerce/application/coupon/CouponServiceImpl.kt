package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.event.CouponIssuedEvent
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.DistributedLock
import com.hhplus.ecommerce.domain.coupon.entity.*
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import com.hhplus.ecommerce.infrastructure.kafka.CouponEventProducer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

/**
 * Redis + Kafka 기반 쿠폰 서비스 (운영 환경)
 * load-test 프로파일이 아닌 경우에만 활성화.
 */
@Service
@Profile("!load-test")
class CouponServiceImpl(
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val couponEventProducer: CouponEventProducer? = null
) : CouponService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 쿠폰 발급 (동기 - DB/Redis 분산락)
     */
    @DistributedLock(
        key = "'coupon:issue:' + #couponId",
        waitTimeMs = 3000,
        leaseTimeMs = 10000,
        errorMessage = "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요.",
        unlockAfterCommit = true
    )
    override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        // 트랜잭션 로직 실행 (커밋까지 완료)
        val result = issueCouponTransaction(couponId, request)

        // 트랜잭션 커밋 완료 후 Kafka 이벤트 발행
        couponEventProducer?.let {
            try {
                val event = CouponIssuedEvent(
                    userCouponId = result.userCouponId,
                    userId = result.userId,
                    couponId = result.couponId,
                    couponName = result.couponName,
                    discountRate = result.discountRate,
                    issuedAt = LocalDateTime.parse(result.issuedAt, DATETIME_FORMATTER),
                    expiresAt = LocalDateTime.parse(result.expiresAt, DATETIME_FORMATTER)
                )
                it.sendCouponIssuedEvent(event)
                logger.info("Kafka 이벤트 발행 성공 - userCouponId: ${result.userCouponId}")
            } catch (e: Exception) {
                logger.error("Kafka 이벤트 발행 실패 - userCouponId: ${result.userCouponId}", e)
            }
        }

        return result
    }

    /**
     * 쿠폰 발급 트랜잭션 로직
     * - 트랜잭션 커밋 후 Kafka 이벤트를 발행하기 위해 분리
     */
    @Transactional
    private fun issueCouponTransaction(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        // 비관적 락으로 쿠폰 조회
        val coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow { CouponNotFoundException(couponId) }

        // 중복 발급 검증
        val existingUserCoupon = userCouponRepository.findFirstByUserIdAndCouponId(request.userId, couponId)
        if (existingUserCoupon != null) {
            throw CouponAlreadyIssuedException(request.userId, couponId)
        }

        // 발급 기간 검증
        val today = LocalDate.now()
        val startDate = coupon.startDate.toLocalDate()
        val endDate = coupon.endDate.toLocalDate()

        if (today.isBefore(startDate)) {
            throw InvalidCouponDateException("The coupon issuance period has not started.")
        }
        if (today.isAfter(endDate)) {
            throw InvalidCouponDateException("The coupon issuance period has ended.")
        }

        // 재고 검증
        coupon.validateIssuable(couponId)

        // 발급 수량 증가
        coupon.issuedQuantity++
        couponRepository.save(coupon)

        // 사용자 쿠폰 생성
        val now = LocalDateTime.now()
        val expiresAt = now.plusDays(coupon.validityDays.toLong())

        val userCoupon = UserCoupon(
            userId = request.userId,
            couponId = couponId,
            status = CouponStatus.AVAILABLE,
            issuedAt = now,
            expiresAt = expiresAt,
            usedAt = null
        )

        val savedUserCoupon = userCouponRepository.save(userCoupon)

        return IssueCouponResult(
            userCouponId = savedUserCoupon.id!!,
            userId = savedUserCoupon.userId,
            couponId = coupon.id!!,
            couponName = coupon.name,
            discountRate = coupon.discountRate,
            status = savedUserCoupon.status.name,
            issuedAt = savedUserCoupon.issuedAt.toString(),
            expiresAt = savedUserCoupon.expiresAt.toString(),
            remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
            totalQuantity = coupon.totalQuantity
        )
    }

    /**
     * 쿠폰 발급 요청 (비동기 - Redis Queue)
     *
     * 중복 요청 검증 (Redis Set)
     * 재고 검증 (Redis Count)
     * 대기열 적재 (Redis List)
     */
    override fun requestCouponIssuance(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        val key = "coupon:$couponId"
        val queueKey = "coupon:issue:queue"

        // 중복 발급 검증 (Redis Set - SADD 결과로 판단)
        // SADD는 추가된 멤버 수를 반환: 0이면 이미 존재(중복), 1이면 새로 추가됨
        val addedCount = redisTemplate.opsForSet().add("$key:users", request.userId.toString()) ?: 0
        if (addedCount == 0L) {
            throw CouponAlreadyIssuedException(request.userId, couponId)
        }

        // 쿠폰 정보 조회 (재고 확인용)
        // 실제로는 캐싱된 정보를 사용해야 성능 이점이 있으나, 여기서는 DB 조회 사용
        val coupon = findCouponById(couponId)

        // 발급 기간 검증
        val today = LocalDate.now()
        if (today.isBefore(coupon.startDate.toLocalDate()) || today.isAfter(coupon.endDate.toLocalDate())) {
            // 중복 방지 Set에서 롤백
            redisTemplate.opsForSet().remove("$key:users", request.userId.toString())
            throw InvalidCouponDateException("Coupon is not in issuance period")
        }

        // 재고 검증 (Redis Count)
        val count = redisTemplate.opsForValue().increment("$key:count") ?: 0
        if (count > coupon.totalQuantity) {
            // 증가시킨 값 롤백 (정확하지 않을 수 있으나, 초과 방지가 목적)
            redisTemplate.opsForValue().decrement("$key:count")
            // 중복 방지 Set에서도 롤백
            redisTemplate.opsForSet().remove("$key:users", request.userId.toString())
            throw CouponOutOfStockException("Sold out")
        }

        // Queue 적재
        redisTemplate.opsForList().rightPush(queueKey, "$couponId:${request.userId}")

        // 가상의 응답 생성 (비동기 처리이므로 ID 등은 임시 값이나 null)
        return IssueCouponResult(
            userCouponId = UUID.randomUUID(), // 실제 ID는 나중에 생성됨
            userId = request.userId,
            couponId = couponId,
            couponName = coupon.name,
            discountRate = coupon.discountRate,
            status = "QUEUED", // 상태 표시
            issuedAt = LocalDateTime.now().toString(),
            expiresAt = LocalDateTime.now().plusDays(coupon.validityDays.toLong()).toString(),
            remainingQuantity = coupon.totalQuantity - count.toInt(),
            totalQuantity = coupon.totalQuantity
        )
    }

    override fun getAvailableCoupons(): AvailableCouponItemResult {
        val availableCoupons = couponRepository.findAvailableCoupons(LocalDateTime.now())

        val couponItems = availableCoupons.map { coupon ->
            AvailableCouponItemDto(
                id = coupon.id!!,
                couponName = coupon.name,
                description = coupon.description,
                discountRate = coupon.discountRate,
                remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
                totalQuantity = coupon.totalQuantity,
                issuePeriod = IssuePeriodDto(
                    startDate = coupon.startDate.toString(),
                    endDate = coupon.endDate.toString()
                ),
                validityDays = coupon.validityDays
            )
        }

        return AvailableCouponItemResult(coupons = couponItems)
    }

    override fun getCouponDetail(couponId: UUID): CouponDetailResult {
        val coupon = findCouponById(couponId)

        // 발급 가능 여부 판단
        val today = LocalDate.now()
        val startDate = coupon.startDate.toLocalDate()
        val endDate = coupon.endDate.toLocalDate()
        val isInPeriod = !today.isBefore(startDate) && !today.isAfter(endDate)
        val hasStock = coupon.issuedQuantity < coupon.totalQuantity
        val isAvailable = isInPeriod && hasStock

        return CouponDetailResult(
            id = coupon.id!!,
            couponName = coupon.name,
            description = coupon.description,
            discountRate = coupon.discountRate,
            totalQuantity = coupon.totalQuantity,
            issuedQuantity = coupon.issuedQuantity,
            remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
            issuePeriod = IssuePeriodDto(
                startDate = coupon.startDate.toString(),
                endDate = coupon.endDate.toString()
            ),
            validityDays = coupon.validityDays,
            isAvailable = isAvailable,
            createdAt = coupon.createdAt.toString()
        )
    }

    @Transactional(readOnly = true)
    override fun getUserCoupons(userId: UUID, status: CouponStatus?): UserCouponListResult {
        val userCoupons = userCouponRepository.findByUserId(userId)

        // 필터 적용 (status가 null이면 전체)
        val filtered = if (status != null) {
            userCoupons.filter { it.status == status }
        } else {
            userCoupons
        }

        val couponIds = filtered.map { it.couponId }.distinct()
        val coupons = if (couponIds.isNotEmpty()) {
            couponRepository.findAllById(couponIds)
        } else {
            emptyList()
        }
        val couponMap = coupons.associateBy { it.id!! }

        val now = LocalDateTime.now()

        val items = filtered.map { uc ->
            // 캐시된 쿠폰 정보 조회
            val coupon = couponMap[uc.couponId]
                ?: throw CouponNotFoundException(uc.couponId)

            val couponName = coupon.name
            val discountRate = coupon.discountRate

            // expiresAt parsing: 저장된 포맷 "yyyy-MM-dd HH:mm:ss"
            val expiresAtDate = try {
                LocalDateTime.parse(uc.expiresAt.toString(), DATETIME_FORMATTER)
            } catch (e: Exception) {
                // 파싱 실패 시 현재시간으로 세팅(안전하게 만료로 처리)
                now
            }

            val isExpired = expiresAtDate.isBefore(now)
            val daysRemaining = if (!isExpired) {
                val diff = ChronoUnit.DAYS.between(now.toLocalDate(), expiresAtDate.toLocalDate()).toInt()
                max(diff, 0)
            } else {
                0
            }

            UserCouponItemDto(
                userCouponId = uc.id!!,
                couponId = uc.couponId,
                couponName = couponName,
                discountRate = discountRate,
                status = uc.status.name,
                issuedAt = uc.issuedAt.toString(),
                expiresAt = uc.expiresAt.toString(),
                usedAt = uc.usedAt.toString(),
                isExpired,
                daysRemaining
            )
        }

        val summary = UserCouponSummaryDto(
            totalCount = userCoupons.size,
            availableCount = userCoupons.count { it.status == CouponStatus.AVAILABLE },
            usedCount = userCoupons.count { it.status == CouponStatus.USED },
            expiredCount = userCoupons.count { it.status == CouponStatus.EXPIRED }
        )

        return UserCouponListResult(
            userId = userId,
            coupons = items,
            summary = summary
        )
    }

    override fun getUserCoupon(userId: UUID, userCouponId: UUID): UserCouponResult {
        val userCoupon = userCouponRepository.findByIdAndUserId(id = userCouponId, userId)
            ?: throw UserCouponNotFoundException(userId, userCouponId)
        val coupon = findCouponById(userCoupon.couponId)

        val couponName = coupon?.name
        val description = coupon?.description ?: ""
        val discountRate = coupon?.discountRate ?: 0

        val now = LocalDateTime.now()
        val expiresAtDate = try {
            parseDateTimeFlexible(userCoupon.expiresAt.toString())
        } catch (ex: ResponseStatusException) {
            // 파싱 실패 시 현재시간을 만료로 처리
            now
        }

        val isExpired = expiresAtDate.isBefore(now) || expiresAtDate.isEqual(now).not() && expiresAtDate.toLocalDate().isBefore(now.toLocalDate())
        val canUse = (userCoupon.status == CouponStatus.AVAILABLE) && !isExpired

        return UserCouponResult(
            id = userCoupon.id!!,
            userId = userCoupon.userId,
            couponId = userCoupon.couponId,
            couponName = couponName!!,
            description = description,
            discountRate = discountRate,
            status = userCoupon.status,
            issuedAt = userCoupon.issuedAt.toString(),
            expiresAt = userCoupon.expiresAt.toString(),
            usedAt = userCoupon.usedAt.toString(),
            isExpired = isExpired,
            canUse = canUse
        )
    }

    override fun findCouponById(id: UUID): Coupon {
        return couponRepository.findById(id)
            .orElseThrow{ CouponNotFoundException(id) }
    }

    override fun findUserCoupon(userId: UUID, couponId: UUID): UserCoupon {
        return userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId)
            ?: throw UserCouponNotFoundException(userId, couponId)
    }

    override fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon {
        return userCouponRepository.save(userCoupon)
    }

    override fun findByIdWithLock(id: UUID): Coupon {
        return couponRepository.findByIdWithLock(id)
            .orElseThrow { CouponNotFoundException(id) }
    }

    /**
     * 배치로 쿠폰 발급 (스케줄러 전용)
     */
    @Transactional
    override fun issueCouponBatch(requests: List<Pair<UUID, UUID>>): Int {
        if (requests.isEmpty()) return 0

        // couponId별로 그룹화
        val groupedByCoupon = requests.groupBy { it.first }

        var successCount = 0
        val now = LocalDateTime.now()

        // 각 쿠폰별로 배치 처리
        for ((couponId, userRequests) in groupedByCoupon) {
            try {
                // 쿠폰 조회 (비관적 락)
                val coupon = couponRepository.findByIdWithLock(couponId)
                    .orElseThrow { CouponNotFoundException(couponId) }

                // 발급할 수량 계산
                val issueCount = userRequests.size

                // 재고 검증
                if (coupon.issuedQuantity + issueCount > coupon.totalQuantity) {
                    // 재고 부족 시 발급 가능한 만큼만 처리
                    val availableCount = coupon.totalQuantity - coupon.issuedQuantity
                    if (availableCount <= 0) {
                        continue // 다음 쿠폰으로
                    }
                    // 가능한 만큼만 처리
                    val validRequests = userRequests.take(availableCount)

                    // UserCoupon 엔티티 생성
                    val userCoupons = validRequests.map { (_, userId) ->
                        UserCoupon(
                            userId = userId,
                            couponId = couponId,
                            status = CouponStatus.AVAILABLE,
                            issuedAt = now,
                            expiresAt = now.plusDays(coupon.validityDays.toLong()),
                            usedAt = null
                        )
                    }

                    // 배치 저장
                    userCouponRepository.saveAll(userCoupons)

                    // 재고 업데이트
                    coupon.issuedQuantity += availableCount
                    couponRepository.save(coupon)

                    successCount += availableCount
                } else {
                    // 전체 발급 가능
                    val userCoupons = userRequests.map { (_, userId) ->
                        UserCoupon(
                            userId = userId,
                            couponId = couponId,
                            status = CouponStatus.AVAILABLE,
                            issuedAt = now,
                            expiresAt = now.plusDays(coupon.validityDays.toLong()),
                            usedAt = null
                        )
                    }

                    // 배치 저장
                    userCouponRepository.saveAll(userCoupons)

                    // 재고 업데이트
                    coupon.issuedQuantity += issueCount
                    couponRepository.save(coupon)

                    successCount += issueCount
                }
            } catch (e: Exception) {
                // 해당 쿠폰 그룹은 실패하지만 다른 쿠폰은 계속 처리
                // 로깅은 스케줄러에서 처리
                throw e // 또는 무시하고 계속
            }
        }

        return successCount
    }

    private fun parseDateTimeFlexible(dateTimeStr: String): LocalDateTime {
        // 허용 포맷: "yyyy-MM-dd HH:mm:ss" 또는 ISO "yyyy-MM-dd'T'HH:mm:ss"
        return try {
            LocalDateTime.parse(dateTimeStr, DATETIME_FORMATTER)
        } catch (e1: DateTimeParseException) {
            try {
                LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e2: DateTimeParseException) {
                try {
                    LocalDateTime.parse(dateTimeStr.replace('T', ' '), DATETIME_FORMATTER)
                } catch (e3: DateTimeParseException) {
                    throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid date format: $dateTimeStr")
                }
            }
        }
    }
}
