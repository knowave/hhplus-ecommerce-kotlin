package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.RedisDistributedLock
import com.hhplus.ecommerce.common.lock.LockAcquisitionFailedException
import com.hhplus.ecommerce.domain.coupon.entity.*
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
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

@Service
class CouponServiceImpl(
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository,
    private val redisDistributedLock: RedisDistributedLock
) : CouponService {
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 쿠폰 발급
     *
     * 선착순 쿠폰 발급을 위한 2단계 동시성 제어:
     *
     * 1단계: Redis 분산 락 (외부 계층)
     *   - 멀티 서버 환경에서 동시성 제어
     *   - 대량 요청을 Redis 레벨에서 제어하여 DB 부하 감소
     *   - 락 획득 실패 시 즉시 예외 반환 (빠른 실패)
     *
     * 2단계: DB 비관적 락 (내부 계층)
     *   - Redis 장애 시에도 데이터 정합성 보장 (최종 방어선)
     *   - issuedQuantity 증가의 원자성 보장
     *
     * 왜 Redis 분산 락만으로는 부족한가?
     * - Redis 장애 시 동시성 제어 불가능
     * - 네트워크 분할 등으로 인한 데이터 불일치 가능
     * - DB가 최종 진실의 원천(Source of Truth)이므로 DB 레벨 보호 필요
     *
     * 왜 비관적 락만으로는 부족한가?
     * - 대량 요청 시 DB 커넥션 풀 고갈
     * - 모든 요청이 DB에 부하를 주어 성능 저하
     * - 락 대기 시간 증가로 타임아웃 발생 가능성
     */
    override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        val lockKey = "coupon:issue:$couponId"

        // Redis 분산 락 획득 시도 (대기 시간: 3초, 락 보유 시간: 10초)
        return redisDistributedLock.executeWithLock(
            lockKey = lockKey,
            waitTimeMs = 3000,
            leaseTimeMs = 10000
        ) {
            // 트랜잭션 시작: Redis 락 내에서 DB 작업 수행
            issueCouponInternal(couponId, request)
        }
    }

    /**
     * 쿠폰 발급 내부 로직 (Redis 락 획득 후 실행)
     */
    @Transactional
    private fun issueCouponInternal(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
        // DB 비관적 락을 사용하여 쿠폰 조회 (이중 안전장치)
        val coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow { CouponNotFoundException(couponId) }

        // 1) 중복 발급 검증 (1인 1매 제한)
        val existingUserCoupon = userCouponRepository.findFirstByUserIdAndCouponId(request.userId, couponId)
        if (existingUserCoupon != null) {
            throw CouponAlreadyIssuedException(request.userId, couponId)
        }

        // 2) 발급 기간 검증
        val today = LocalDate.now()
        val startDate = coupon.startDate.toLocalDate()
        val endDate = coupon.endDate.toLocalDate()

        if (today.isBefore(startDate)) {
            throw InvalidCouponDateException("The coupon issuance period has not started.")
        }
        if (today.isAfter(endDate)) {
            throw InvalidCouponDateException("The coupon issuance period has ended.")
        }

        // 3) 재고 검증 (동시성 제어의 핵심)
        if (coupon.issuedQuantity >= coupon.totalQuantity) {
            throw CouponSoldOutException(couponId)
        }

        // 4) 발급 수량 증가 (비관적 락으로 원자적 연산 보장)
        coupon.issuedQuantity++
        couponRepository.save(coupon)

        // 5) 사용자 쿠폰 생성
        // issuedQuantity 증가와 실제 쿠폰 생성이 원자적으로 처리되어야 재고 불일치 방지
        val now = LocalDateTime.now()
        val expiresAt = now.plusDays(coupon.validityDays.toLong())

        val userCoupon = UserCoupon(
            userId = request.userId,
            couponId = couponId,
            status = CouponStatus.AVAILABLE,
            issuedAt = LocalDateTime.now(),
            expiresAt = expiresAt,
            usedAt = null
        )

        val savedUserCoupon = userCouponRepository.save(userCoupon)

        // 6) 응답 생성
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
        val coupon = couponRepository.findById(couponId)
            .orElseThrow { CouponNotFoundException(couponId) }

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
        val coupon = couponRepository.findById(userCoupon.couponId)
            .orElseThrow{ CouponNotFoundException(couponId = userCoupon.couponId) }

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