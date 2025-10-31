package com.hhplus.ecommerce.domains.coupon.dto

data class IssueCouponResponse(
    val userCouponId: Long,
    val userId: Long,
    val couponId: Long,
    val couponName: String,
    val discountRate: Int,
    val status: String,
    val issuedAt: String,
    val expiresAt: String,
    val remainingQuantity: Int,
    val totalQuantity: Int
)