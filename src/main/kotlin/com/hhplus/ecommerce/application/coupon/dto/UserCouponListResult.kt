package com.hhplus.ecommerce.application.coupon.dto

data class UserCouponListResult(
    val userId: Long,
    val coupons: List<UserCouponItemDto>,
    val summary: UserCouponSummaryDto
)

data class UserCouponItemDto(
    val userCouponId: Long,
    val couponId: Long,
    val couponName: String,
    val discountRate: Int,
    val status: String,
    val issuedAt: String,
    val expiresAt: String,
    val usedAt: String?,
    val isExpired: Boolean,
    val daysRemaining: Int
)

data class UserCouponSummaryDto(
    val totalCount: Int,
    val availableCount: Int,
    val usedCount: Int,
    val expiredCount: Int
)

