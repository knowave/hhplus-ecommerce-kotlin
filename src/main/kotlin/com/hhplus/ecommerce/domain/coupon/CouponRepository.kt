package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon

interface CouponRepository {
    fun findById(couponId: Long): Coupon?

    fun findAll(): List<Coupon>

    fun findAvailableCoupons(): List<Coupon>

    fun save(coupon: Coupon): Coupon

    fun findUserCoupon(userId: Long, couponId: Long): UserCoupon?

    fun saveUserCoupon(userCoupon: UserCoupon): UserCoupon

    fun generateUserCouponId(): Long

    fun findUserCouponsByUserId(userId: Long): List<UserCoupon>

    fun findUserCouponByIdAndUserId(id: Long, userId: Long): UserCoupon?
}

enum class CouponStatus(val description: String) {
    AVAILABLE("사용 가능"),
    USED("사용 완료"),
    EXPIRED("만료")
}