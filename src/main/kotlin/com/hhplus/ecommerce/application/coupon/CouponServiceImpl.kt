package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.DistributedLock
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
    private val userCouponRepository: UserCouponJpaRepository
) : CouponService {
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 쿠폰 발급
     *
     * 선착순 쿠폰 발급을 위한 Redis 분산 락 기반 동시성 제어:
     *
     * Redis 분산 락의 역할:
     *   - 멀티 서버 환경에서 동시성 제어
     *   - 대량 요청을 Redis 레벨에서 제어하여 DB 부하 최소화
     *   - 락 획득 실패 시 즉시 예외 반환 (빠른 실패)
     *   - 빠른 락 획득/해제로 높은 처리량 확보
     *
     * 왜 Redis 분산 락을 선택했는가?
     * - 선착순 이벤트는 대량의 동시 요청 발생
     * - DB 비관적 락만 사용 시 커넥션 풀 고갈 위험
     * - Redis는 메모리 기반으로 빠른 락 처리 가능
     * - 트랜잭션 범위를 최소화하여 DB 부하 감소
     *
     * 데이터 정합성 보장 (핵심):
     * - Redis 분산 락 안에서 트랜잭션 완전 커밋
     * - @DistributedLock 어노테이션으로 AOP 기반 락 관리
     * - 트랜잭션 커밋 후 Redis 락 해제 (순서 보장)
     * - 다음 요청이 최신 데이터를 읽도록 보장
     */
    @DistributedLock(
        key = "'coupon:issue:' + #couponId",
        waitTimeMs = 3000,
        leaseTimeMs = 10000,
        errorMessage = "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요.",
        unlockAfterCommit = true
    )
    @Transactional
    override fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult {
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
        if (coupon.issuedQuantity >= coupon.totalQuantity) {
            throw CouponSoldOutException(couponId)
        }

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