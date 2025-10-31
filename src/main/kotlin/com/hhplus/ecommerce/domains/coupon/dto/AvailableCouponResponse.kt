package com.hhplus.ecommerce.domains.coupon.dto

data class AvailableCouponResponse(
    val coupons: List<AvailableCouponItem>
)

data class AvailableCouponItem(
    val id: Long,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    val remainingQuantity: Int,
    val totalQuantity: Int,
    val issuePeriod: IssuePeriod,
    val validityDays: Int
)