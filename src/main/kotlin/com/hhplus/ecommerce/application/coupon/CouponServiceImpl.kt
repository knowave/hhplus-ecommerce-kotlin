package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import com.hhplus.ecommerce.common.exception.InvalidCouponDateException
import com.hhplus.ecommerce.common.exception.UserCouponNotFoundException
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.presentation.coupon.dto.AvailableCouponItem
import com.hhplus.ecommerce.presentation.coupon.dto.AvailableCouponResponse
import com.hhplus.ecommerce.presentation.coupon.dto.CouponDetailResponse
import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponRequest
import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponResponse
import com.hhplus.ecommerce.presentation.coupon.dto.IssuePeriod
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import com.hhplus.ecommerce.presentation.coupon.dto.UserCouponItem
import com.hhplus.ecommerce.presentation.coupon.dto.UserCouponListResponse
import com.hhplus.ecommerce.presentation.coupon.dto.UserCouponResponse
import com.hhplus.ecommerce.presentation.coupon.dto.UserCouponSummary
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max

@Service
class CouponServiceImpl(
    private val couponRepository: CouponRepository
) : CouponService {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val couponLocks = ConcurrentHashMap<Long, ReentrantLock>()
    private val LOCK_TIMEOUT_SECONDS = 5L

    override fun issueCoupon(couponId: Long, request: IssueCouponRequest): IssueCouponResponse {
        // 1. 해당 쿠폰 ID에 대한 락 획득 (없으면 새로 생성)
        // computeIfAbsent: 원자적으로 락 생성 - 여러 스레드가 동시에 호출해도 하나만 생성됨
        val lock = couponLocks.computeIfAbsent(couponId) { ReentrantLock(false) }

        // 2. tryLock으로 타임아웃 내에 락 획득 시도
        // - lock()과 달리 무한 대기하지 않음
        // - InterruptedException 처리 필요
        val lockAcquired = try {
            lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // 스레드가 중단된 경우 인터럽트 상태 복원
            Thread.currentThread().interrupt()
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Coupon issuance processing has been suspended."
            )
        }

        // 3. 락 획득 실패 시 (타임아웃)
        if (!lockAcquired) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "We are currently experiencing a high volume of coupon requests. Please try again later."
            )
        }

        // 4. 락을 획득한 상태에서만 실행
        // try-finally로 반드시 락 해제 보장 (데드락 방지)
        try {
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
            return IssueCouponResponse(
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
        } finally {
            // lock 해제 (필수)
            lock.unlock()
        }
    }

    override fun getAvailableCoupons(): AvailableCouponResponse {
        val availableCoupons = couponRepository.findAvailableCoupons()

        val couponItems = availableCoupons.map { coupon ->
            AvailableCouponItem(
                id = coupon.id,
                couponName = coupon.name,
                description = coupon.description,
                discountRate = coupon.discountRate,
                remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
                totalQuantity = coupon.totalQuantity,
                issuePeriod = IssuePeriod(
                    startDate = coupon.startDate,
                    endDate = coupon.endDate
                ),
                validityDays = coupon.validityDays
            )
        }

        return AvailableCouponResponse(coupons = couponItems)
    }

    override fun getCouponDetail(couponId: Long): CouponDetailResponse {
        val coupon = couponRepository.findById(couponId)
            ?: throw CouponNotFoundException(couponId)

        // 발급 가능 여부 판단
        val today = LocalDate.now()
        val startDate = LocalDate.parse(coupon.startDate, DATE_FORMATTER)
        val endDate = LocalDate.parse(coupon.endDate, DATE_FORMATTER)
        val isInPeriod = !today.isBefore(startDate) && !today.isAfter(endDate)
        val hasStock = coupon.issuedQuantity < coupon.totalQuantity
        val isAvailable = isInPeriod && hasStock

        return CouponDetailResponse(
            id = coupon.id,
            couponName = coupon.name,
            description = coupon.description,
            discountRate = coupon.discountRate,
            totalQuantity = coupon.totalQuantity,
            issuedQuantity = coupon.issuedQuantity,
            remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
            issuePeriod = IssuePeriod(
                startDate = coupon.startDate,
                endDate = coupon.endDate
            ),
            validityDays = coupon.validityDays,
            isAvailable = isAvailable,
            createdAt = coupon.createdAt
        )
    }

    override fun getUserCoupons(userId: Long, status: CouponStatus?): UserCouponListResponse {
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

            UserCouponItem(
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

        val summary = UserCouponSummary(
            totalCount = userCoupons.size,
            availableCount = userCoupons.count { it.status == CouponStatus.AVAILABLE },
            usedCount = userCoupons.count { it.status == CouponStatus.USED },
            expiredCount = userCoupons.count { it.status == CouponStatus.EXPIRED }
        )

        return UserCouponListResponse(
            userId = userId,
            coupons = items,
            summary = summary
        )
    }

    override fun getUserCoupon(userId: Long, userCouponId: Long): UserCouponResponse {
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

        return UserCouponResponse(
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