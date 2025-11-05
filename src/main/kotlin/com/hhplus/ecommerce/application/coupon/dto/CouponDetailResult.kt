package com.hhplus.ecommerce.application.coupon.dto

data class CouponDetailResult(
    val id: Long,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val remainingQuantity: Int,
    val issuePeriod: IssuePeriodDto,
    val validityDays: Int,
    val isAvailable: Boolean,
    val createdAt: String
)
