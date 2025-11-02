package com.hhplus.ecommerce.domains.coupon.dto

data class CouponDetailResponse(
    val id: Long,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val remainingQuantity: Int,
    val issuePeriod: IssuePeriod,
    val validityDays: Int,
    val isAvailable: Boolean,
    val createdAt: String
)