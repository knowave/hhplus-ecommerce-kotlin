package com.hhplus.ecommerce.domains.coupon

import com.hhplus.ecommerce.domains.coupon.dto.Coupon
import com.hhplus.ecommerce.domains.coupon.dto.UserCoupon

interface CouponRepository {
    fun findById(couponId: Long): Coupon?

    fun findAll(): List<Coupon>

    fun findAvailableCoupons(): List<Coupon>

    fun save(coupon: Coupon): Coupon

    fun findUserCoupon(userId: Long, couponId: Long): UserCoupon?

    fun saveUserCoupon(userCoupon: UserCoupon): UserCoupon

    fun generateUserCouponId(): Long

    fun findUserCouponsByUserId(userId: Long): List<UserCoupon>
}