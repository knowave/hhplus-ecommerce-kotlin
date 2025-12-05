package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.*
import java.util.UUID


interface CouponService {
    fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult

    fun requestCouponIssuance(couponId: UUID, request: IssueCouponCommand): IssueCouponResult

    fun getAvailableCoupons(): AvailableCouponItemResult

    fun getCouponDetail(couponId: UUID): CouponDetailResult

    fun getUserCoupons(userId: UUID, status: CouponStatus?): UserCouponListResult

    fun getUserCoupon(userId: UUID, userCouponId: UUID): UserCouponResult

    fun findCouponById(id: UUID): Coupon

    fun findUserCoupon(userId: UUID, couponId: UUID): UserCoupon

    fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon

    /**
     * 비관적 락을 사용하여 쿠폰을 조회
     */
    fun findByIdWithLock(id: UUID): Coupon
}