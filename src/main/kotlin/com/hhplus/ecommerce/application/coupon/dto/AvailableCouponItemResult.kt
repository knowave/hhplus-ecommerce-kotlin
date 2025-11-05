package com.hhplus.ecommerce.application.coupon.dto

data class AvailableCouponItemResult(
    val coupons: List<AvailableCouponItemDto>
)

data class AvailableCouponItemDto(
    val id: Long,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    val remainingQuantity: Int,
    val totalQuantity: Int,
    val issuePeriod: IssuePeriodDto,
    val validityDays: Int
)

data class IssuePeriodDto(
    val startDate: String,
    val endDate: String
)