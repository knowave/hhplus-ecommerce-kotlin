package com.hhplus.ecommerce.application.coupon.dto

import java.util.UUID

data class UserCouponListResult(
    val userId: UUID,
    val coupons: List<UserCouponItemDto>,
    val summary: UserCouponSummaryDto
)

data class UserCouponItemDto(
    val userCouponId: UUID,
    val couponId: UUID,
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

