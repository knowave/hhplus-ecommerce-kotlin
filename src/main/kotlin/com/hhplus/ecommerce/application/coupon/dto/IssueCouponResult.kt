package com.hhplus.ecommerce.application.coupon.dto

import java.util.UUID

data class IssueCouponResult(
    val userCouponId: UUID,
    val userId: UUID,
    val couponId: UUID,
    val couponName: String,
    val discountRate: Int,
    val status: String,
    val issuedAt: String,
    val expiresAt: String,
    val remainingQuantity: Int,
    val totalQuantity: Int
)
