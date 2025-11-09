package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import java.util.UUID

interface CouponRepository {
    fun findById(couponId: UUID): Coupon?

    fun findAll(): List<Coupon>

    fun findAvailableCoupons(): List<Coupon>

    fun save(coupon: Coupon): Coupon

    fun findUserCoupon(userId: UUID, couponId: UUID): UserCoupon?

    fun saveUserCoupon(userCoupon: UserCoupon): UserCoupon

    fun findUserCouponsByUserId(userId: UUID): List<UserCoupon>

    fun findUserCouponByIdAndUserId(id: UUID, userId: UUID): UserCoupon?
}

enum class CouponStatus(val description: String) {
    AVAILABLE("사용 가능"),
    USED("사용 완료"),
    EXPIRED("만료")
}