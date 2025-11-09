package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.*


interface CouponService {
    fun issueCoupon(couponId: Long, request: IssueCouponCommand): IssueCouponResult

    fun getAvailableCoupons(): AvailableCouponItemResult

    fun getCouponDetail(couponId: Long): CouponDetailResult

    fun getUserCoupons(userId: Long, status: CouponStatus?): UserCouponListResult

    fun getUserCoupon(userId: Long, userCouponId: Long): UserCouponResult

    fun findCouponById(id: Long): Coupon

    fun findUserCoupon(userId: Long, couponId: Long): UserCoupon

    fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon
}