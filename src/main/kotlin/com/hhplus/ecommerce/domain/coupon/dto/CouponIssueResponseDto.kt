package com.hhplus.ecommerce.domain.coupon.dto

import java.time.LocalDateTime

data class CouponIssueResponseDto(
    val userCouponId: String,
    val couponName: String,
    val discountRate: Int,
    val expiresAt: String,
    val remainingQuantity: Int
) {
    constructor(
        userCouponId: String,
        couponName: String,
        discountRate: Int,
        expiresAt: LocalDateTime,
        remainingQuantity: Int
    ) : this(
        userCouponId = userCouponId,
        couponName = couponName,
        discountRate = discountRate,
        expiresAt = expiresAt.toString(),
        remainingQuantity = remainingQuantity
    )
}