package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.coupon.entity.*
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
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
 * DB 기반 쿠폰 서비스 (부하 테스트 환경)
 *
 * Redis, Kafka 없이 순수 DB의 비관적 락만 사용하여 쿠폰 발급 관리.
 * load-test 프로파일일 때 활성화.
 *
 * 특징:
 * - Redis 분산락 대신 DB 비관적 락(PESSIMISTIC_WRITE) 사용
 * - Kafka 이벤트 발행 없음
 * - 모든 요청을 동기적으로 처리
 *
 * 부하 테스트 목적:
 * - Redis/Kafka 없이 DB 락만 사용 시 성능 측정
 * - 동시성 제어를 DB에서만 처리할 때의 처리량 확인
 */
@Service
@Profile("load-test")
class CouponServiceDbImpl(
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository
) : CouponService {

    companion object {
        private val logger = LoggerFactory.getLogger(CouponServiceDbImpl::class.java)
        private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /**
     * 쿠폰 발급 (DB 비관적 락 사용)
     *
     * Redis 분산락 없이 DB 비관적 락만으로 동시성 제어.
     * Kafka 이벤트 발행 없이 동기적으로 처리.
     */
    @Transactional
    override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        logger.debug("DB 비관적 락 방식으로 쿠폰 발급 시작 - couponId: {}, userId: {}", couponId, request.userId)

        // 비관적 락으로 쿠폰 조회 (다른 트랜잭션이 이 행을 수정할 수 없음)
        val coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow { CouponNotFoundException(couponId) }

        // 중복 발급 검증
        val existingUserCoupon = userCouponRepository.findFirstByUserIdAndCouponId(request.userId, couponId)
        if (existingUserCoupon != null) {
            logger.warn("중복 발급 시도 - userId: {}, couponId: {}", request.userId, couponId)
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

        logger.info("쿠폰 발급 완료 - userCouponId: {}, userId: {}, couponId: {}",
            savedUserCoupon.id, request.userId, couponId)

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
     * 쿠폰 발급 요청 (비동기 미지원)
     *
     * DB 기반 구현에서는 비동기 처리를 지원하지 않으므로,
     * 동기 방식(issueCoupon)으로 바로 처리합니다.
     */
    override fun requestCouponIssuance(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        logger.debug("requestCouponIssuance 호출 -> issueCoupon으로 동기 처리")
        return issueCoupon(couponId, request)
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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
            val coupon = couponMap[uc.couponId]
                ?: throw CouponNotFoundException(uc.couponId)

            val couponName = coupon.name
            val discountRate = coupon.discountRate

            val expiresAtDate = try {
                LocalDateTime.parse(uc.expiresAt.toString(), DATETIME_FORMATTER)
            } catch (e: Exception) {
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

    @Transactional(readOnly = true)
    override fun getUserCoupon(userId: UUID, userCouponId: UUID): UserCouponResult {
        val userCoupon = userCouponRepository.findByIdAndUserId(id = userCouponId, userId)
            ?: throw UserCouponNotFoundException(userId, userCouponId)
        val coupon = findCouponById(userCoupon.couponId)

        val couponName = coupon.name
        val description = coupon.description
        val discountRate = coupon.discountRate

        val now = LocalDateTime.now()
        val expiresAtDate = try {
            parseDateTimeFlexible(userCoupon.expiresAt.toString())
        } catch (ex: ResponseStatusException) {
            now
        }

        val isExpired = expiresAtDate.isBefore(now) || expiresAtDate.isEqual(now).not() && expiresAtDate.toLocalDate().isBefore(now.toLocalDate())
        val canUse = (userCoupon.status == CouponStatus.AVAILABLE) && !isExpired

        return UserCouponResult(
            id = userCoupon.id!!,
            userId = userCoupon.userId,
            couponId = userCoupon.couponId,
            couponName = couponName,
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

    @Transactional(readOnly = true)
    override fun findCouponById(id: UUID): Coupon {
        return couponRepository.findById(id)
            .orElseThrow { CouponNotFoundException(id) }
    }

    @Transactional(readOnly = true)
    override fun findUserCoupon(userId: UUID, couponId: UUID): UserCoupon {
        return userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId)
            ?: throw UserCouponNotFoundException(userId, couponId)
    }

    @Transactional
    override fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon {
        return userCouponRepository.save(userCoupon)
    }

    @Transactional(readOnly = true)
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
                logger.error("배치 발급 실패 - couponId: {}", couponId, e)
                throw e
            }
        }

        return successCount
    }

    private fun parseDateTimeFlexible(dateTimeStr: String): LocalDateTime {
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