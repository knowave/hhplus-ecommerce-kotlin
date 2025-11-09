package com.hhplus.ecommerce.domain.coupon.repository

import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserCouponJpaRepository : JpaRepository<UserCoupon, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): UserCoupon?

    fun findByUserId(userId: UUID): List<UserCoupon>

    fun findFirstByUserIdAndCouponId(userId: UUID, couponId: UUID): UserCoupon?
}