package com.hhplus.ecommerce.domain.coupon.dto

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import java.time.LocalDateTime

data class CouponResponseDto(
    val couponId: String,
    val name: String,
    val discountRate: Int,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val remainingQuantity: Int,
    val startDate: String,
    val endDate: String
) {
    constructor(coupon: Coupon) : this(
        couponId = coupon.id,
        name = coupon.name,
        discountRate = coupon.discountRate,
        totalQuantity = coupon.totalQuantity,
        issuedQuantity = coupon.issuedQuantity,
        remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity,
        startDate = coupon.startDate.toString(),
        endDate = coupon.endDate.toString()
    )
}