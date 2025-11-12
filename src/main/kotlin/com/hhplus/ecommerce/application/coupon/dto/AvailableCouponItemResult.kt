package com.hhplus.ecommerce.application.coupon.dto

import java.util.UUID

data class AvailableCouponItemResult(
    val coupons: List<AvailableCouponItemDto>
)

data class AvailableCouponItemDto(
    val id: UUID,
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