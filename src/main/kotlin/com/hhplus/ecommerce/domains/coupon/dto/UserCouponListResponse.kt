package com.hhplus.ecommerce.domains.coupon.dto

data class UserCouponListResponse(
    val userId: Long,
    val coupons: List<UserCouponItem>,
    val summary: UserCouponSummary
)

data class UserCouponItem(
    val userCouponId: Long,
    val couponId: Long,
    val couponName: String,
    val discountRate: Int,
    val status: String,
    val issuedAt: String,
    val expiresAt: String,
    val usedAt: String?
)

data class UserCouponSummary(
    val totalCount: Int,
    val availableCount: Int,
    val usedCount: Int,
    val expiredCount: Int
)