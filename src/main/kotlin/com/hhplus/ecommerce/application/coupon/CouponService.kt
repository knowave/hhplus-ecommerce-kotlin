package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.infrastructure.coupon.CouponStatus
import com.hhplus.ecommerce.presentation.coupon.dto.AvailableCouponResponse
import com.hhplus.ecommerce.presentation.coupon.dto.CouponDetailResponse
import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponRequest
import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponResponse
import com.hhplus.ecommerce.presentation.coupon.dto.UserCouponListResponse
import com.hhplus.ecommerce.presentation.coupon.dto.UserCouponResponse


interface CouponService {
    fun issueCoupon(couponId: Long, request: IssueCouponRequest): IssueCouponResponse

    fun getAvailableCoupons(): AvailableCouponResponse

    fun getCouponDetail(couponId: Long): CouponDetailResponse

    fun getUserCoupons(userId: Long, status: CouponStatus?): UserCouponListResponse

    fun getUserCoupon(userId: Long, userCouponId: Long): UserCouponResponse
}