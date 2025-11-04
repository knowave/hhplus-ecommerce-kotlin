package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
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

    fun findCouponById(id: Long): Coupon

    fun findUserCoupon(userId: Long, couponId: Long): UserCoupon

    fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon
}