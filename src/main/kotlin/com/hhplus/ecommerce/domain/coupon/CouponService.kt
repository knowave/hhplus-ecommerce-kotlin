package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.coupon.dto.CouponIssueResponseDto
import com.hhplus.ecommerce.domain.coupon.dto.CouponResponseDto
import com.hhplus.ecommerce.domain.coupon.entity.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import com.hhplus.ecommerce.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun issueCoupon(userId: String, couponId: String): CouponIssueResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        val coupon = couponRepository.findByIdWithOptimisticLock(couponId)
            .orElseThrow { CouponNotFoundException(couponId) }

        val currentTime = LocalDateTime.now()
        if (!coupon.isValid()) {
            throw InvalidCouponException("Coupon is not valid. Valid period: ${coupon.startDate} ~ ${coupon.endDate}")
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw CouponAlreadyIssuedException(userId, couponId)
        }

        coupon.issue()

        val expiresAt = currentTime.plusDays(30)
        val userCoupon = UserCoupon(
            id = UUID.randomUUID().toString(),
            user = user,
            coupon = coupon,
            status = CouponStatus.AVAILABLE,
            issuedAt = currentTime,
            expiresAt = expiresAt
        )

        userCouponRepository.save(userCoupon)

        val remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity
        return CouponIssueResponseDto(
            userCouponId = userCoupon.id,
            couponName = coupon.name,
            discountRate = coupon.discountRate,
            expiresAt = expiresAt,
            remainingQuantity = remainingQuantity
        )
    }

    @Transactional(readOnly = true)
    fun getAllCoupons(): List<CouponResponseDto> {
        val currentTime = LocalDateTime.now()
        return couponRepository.findByStartDateBeforeAndEndDateAfter(currentTime, currentTime)
            .map { CouponResponseDto(it) }
    }

    @Transactional(readOnly = true)
    fun getCouponById(couponId: String): CouponResponseDto {
        val coupon = couponRepository.findById(couponId)
            .orElseThrow { CouponNotFoundException(couponId) }
        return CouponResponseDto(coupon)
    }
}