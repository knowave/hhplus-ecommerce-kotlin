package com.hhplus.ecommerce.domain.coupon

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UserCouponRepository : JpaRepository<UserCoupon, String> {

    fun findByUserId(userId: String): List<UserCoupon>

    fun findByUserIdAndStatus(userId: String, status: CouponStatus): List<UserCoupon>

    fun existsByUserIdAndCouponId(userId: String, couponId: String): Boolean

    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.couponId = :couponId AND uc.status = :status")
    fun findByUserIdAndCouponIdAndStatus(
        userId: String,
        couponId: String,
        status: CouponStatus
    ): UserCoupon?

    fun findByExpiresAtBeforeAndStatus(expiresAt: LocalDateTime, status: CouponStatus): List<UserCoupon>
}
