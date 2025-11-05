package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.coupon.*
import com.hhplus.ecommerce.domain.coupon.entity.*
import com.hhplus.ecommerce.presentation.coupon.dto.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Service
class CouponServiceImpl(
    private val couponRepository: CouponRepository,
    private val lockManager: com.hhplus.ecommerce.common.lock.LockManager
) : CouponService {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun issueCoupon(couponId: Long, request: IssueCouponCommand): IssueCouponResult {
        // Coupon Lock을 사용하여 동시 발급을 제어합니다.
        return lockManager.executeWithCouponLock(couponId) {
            // 쿠폰 조회
            val coupon = couponRepository.findById(couponId)
                ?: throw CouponNotFoundException(couponId)

            // 1) 중복 발급 검증 (1인 1매 제한)
            val existingUserCoupon = couponRepository.findUserCoupon(request.userId, couponId)
            if (existingUserCoupon != null) {
                throw CouponAlreadyIssuedException(request.userId, couponId)
            }

            // 2) 발급 기간 검증
            val today = LocalDate.now()
            val startDate = LocalDate.parse(coupon.startDate, DATE_FORMATTER)
            val endDate = LocalDate.parse(coupon.endDate, DATE_FORMATTER)

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

            // 4) 발급 수량 증가 (원자적 연산 보장)
            coupon.issuedQuantity++
            couponRepository.save(coupon)

            // 5) 사용자 쿠폰 생성
            // issuedQuantity 증가와 실제 쿠폰 생성이 원자적으로 처리되어야 재고 불일치 방지
            val now = LocalDateTime.now()
            val expiresAt = now.plusDays(coupon.validityDays.toLong())

            val userCoupon = UserCoupon(
                id = couponRepository.generateUserCouponId(),
                userId = request.userId,
                couponId = couponId,
                status = CouponStatus.AVAILABLE,
                issuedAt = now.format(DATETIME_FORMATTER),
                expiresAt = expiresAt.format(DATETIME_FORMATTER),
                usedAt = null
            )

            couponRepository.saveUserCoupon(userCoupon)

            // 6) 응답 생성
            IssueCouponResult(
                userCouponId = userCoupon.id,
                userId = userCoupon.userId,
                couponId = coupon.id,
                couponName = coupon.name,
                discountRate = coupon.discountRate,
                status = userCoupon.status.name,
                issuedAt = userCoupon.issuedAt,
                expiresAt = userCoupon.expiresAt,
                remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
                totalQuantity = coupon.totalQuantity
            )
        }
    }

    override fun getAvailableCoupons(): AvailableCouponItemResult {
        val availableCoupons = couponRepository.findAvailableCoupons()

        val couponItems = availableCoupons.map { coupon ->
            AvailableCouponItemDto(
                id = coupon.id,
                couponName = coupon.name,
                description = coupon.description,
                discountRate = coupon.discountRate,
                remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
                totalQuantity = coupon.totalQuantity,
                issuePeriod = IssuePeriodDto(
                    startDate = coupon.startDate,
                    endDate = coupon.endDate
                ),
                validityDays = coupon.validityDays
            )
        }

        return AvailableCouponItemResult(coupons = couponItems)
    }

    override fun getCouponDetail(couponId: Long): CouponDetailResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CouponNotFoundException(couponId)

        // 발급 가능 여부 판단
        val today = LocalDate.now()
        val startDate = LocalDate.parse(coupon.startDate, DATE_FORMATTER)
        val endDate = LocalDate.parse(coupon.endDate, DATE_FORMATTER)
        val isInPeriod = !today.isBefore(startDate) && !today.isAfter(endDate)
        val hasStock = coupon.issuedQuantity < coupon.totalQuantity
        val isAvailable = isInPeriod && hasStock

        return CouponDetailResult(
            id = coupon.id,
            couponName = coupon.name,
            description = coupon.description,
            discountRate = coupon.discountRate,
            totalQuantity = coupon.totalQuantity,
            issuedQuantity = coupon.issuedQuantity,
            remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
            issuePeriod = IssuePeriodDto(
                startDate = coupon.startDate,
                endDate = coupon.endDate
            ),
            validityDays = coupon.validityDays,
            isAvailable = isAvailable,
            createdAt = coupon.createdAt
        )
    }

    override fun getUserCoupons(userId: Long, status: CouponStatus?): UserCouponListResult {
        val userCoupons = couponRepository.findUserCouponsByUserId(userId)

        // 필터 적용 (status가 null이면 전체)
        val filtered = if (status != null) {
            userCoupons.filter { it.status == status }
        } else {
            userCoupons
        }

        val now = LocalDateTime.now()

        val items = filtered.map { uc ->
            // 쿠폰 메타 정보(이름/discountRate) 조회 (없으면 기본값)
            val coupon = couponRepository.findById(uc.couponId)
            val couponName = coupon?.name ?: "Unknown"
            val discountRate = coupon?.discountRate ?: 0

            // expiresAt parsing: 저장된 포맷 "yyyy-MM-dd HH:mm:ss"
            val expiresAtDate = try {
                LocalDateTime.parse(uc.expiresAt, DATETIME_FORMATTER)
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
                userCouponId = uc.id,
                couponId = uc.couponId,
                couponName = couponName,
                discountRate = discountRate,
                status = uc.status.name,
                issuedAt = uc.issuedAt,
                expiresAt = uc.expiresAt,
                usedAt = uc.usedAt,
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

    override fun getUserCoupon(userId: Long, userCouponId: Long): UserCouponResult {
        val userCoupon = couponRepository.findUserCouponByIdAndUserId(id = userCouponId, userId)
            ?: throw UserCouponNotFoundException(userId, userCouponId)
        val coupon = couponRepository.findById(userCoupon.couponId)

        val couponName = coupon?.name
        val description = coupon?.description ?: ""
        val discountRate = coupon?.discountRate ?: 0

        val now = LocalDateTime.now()
        val expiresAtDate = try {
            parseDateTimeFlexible(userCoupon.expiresAt)
        } catch (ex: ResponseStatusException) {
            // 파싱 실패 시 현재시간을 만료로 처리
            now
        }

        val isExpired = expiresAtDate.isBefore(now) || expiresAtDate.isEqual(now).not() && expiresAtDate.toLocalDate().isBefore(now.toLocalDate())
        val canUse = (userCoupon.status == CouponStatus.AVAILABLE) && !isExpired

        return UserCouponResult(
            id = userCoupon.id,
            userId = userCoupon.userId,
            couponId = userCoupon.couponId,
            couponName = couponName!!,
            description = description,
            discountRate = discountRate,
            status = userCoupon.status,
            issuedAt = userCoupon.issuedAt,
            expiresAt = userCoupon.expiresAt,
            usedAt = userCoupon.usedAt,
            isExpired = isExpired,
            canUse = canUse
        )
    }

    override fun findCouponById(id: Long): Coupon {
        return couponRepository.findById(id)
            ?: throw CouponNotFoundException(id)
    }

    override fun findUserCoupon(userId: Long, couponId: Long): UserCoupon {
        return couponRepository.findUserCoupon(userId, couponId)
            ?: throw UserCouponNotFoundException(userId, couponId)
    }

    override fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon {
        return couponRepository.saveUserCoupon(userCoupon)
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