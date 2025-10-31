package com.hhplus.ecommerce.domains.coupon

import com.hhplus.ecommerce.domains.coupon.dto.AvailableCouponResponse
import com.hhplus.ecommerce.domains.coupon.dto.CouponDetailResponse
import com.hhplus.ecommerce.domains.coupon.dto.IssueCouponRequest
import com.hhplus.ecommerce.domains.coupon.dto.IssueCouponResponse
import com.hhplus.ecommerce.domains.coupon.dto.UserCouponListResponse
import com.hhplus.ecommerce.domains.coupon.dto.UserCouponResponse


interface CouponService {
    fun issueCoupon(couponId: Long, request: IssueCouponRequest): IssueCouponResponse

    fun getAvailableCoupons(): AvailableCouponResponse

    fun getCouponDetail(couponId: Long): CouponDetailResponse

    fun getUserCoupons(userId: Long, status: CouponStatus?): UserCouponListResponse

    fun getUserCoupon(userId: Long, userCouponId: Long): UserCouponResponse
}