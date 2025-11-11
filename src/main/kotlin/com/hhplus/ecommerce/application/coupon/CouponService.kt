package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.*
import java.util.UUID


interface CouponService {
    fun issueCoupon(couponId: UUID, request: IssueCouponCommand): IssueCouponResult

    fun getAvailableCoupons(): AvailableCouponItemResult

    fun getCouponDetail(couponId: UUID): CouponDetailResult

    fun getUserCoupons(userId: UUID, status: CouponStatus?): UserCouponListResult

    fun getUserCoupon(userId: UUID, userCouponId: UUID): UserCouponResult

    fun findCouponById(id: UUID): Coupon

    fun findUserCoupon(userId: UUID, couponId: UUID): UserCoupon

    fun updateUserCoupon(userCoupon: UserCoupon): UserCoupon

    /**
     * 비관적 락을 사용하여 쿠폰을 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용:
     * - 쿠폰 발급 시 issuedQuantity 증가
     * - 쿠폰 복원 시
     */
    fun findByIdWithLock(id: UUID): Coupon
}