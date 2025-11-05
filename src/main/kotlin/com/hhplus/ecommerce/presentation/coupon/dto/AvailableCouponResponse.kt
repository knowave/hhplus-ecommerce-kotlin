package com.hhplus.ecommerce.presentation.coupon.dto

import com.hhplus.ecommerce.application.coupon.dto.AvailableCouponItemDto
import com.hhplus.ecommerce.application.coupon.dto.AvailableCouponItemResult

data class AvailableCouponResponse(
    val coupons: List<AvailableCouponItem>
) {
    companion object {
        fun from(result: AvailableCouponItemResult): AvailableCouponResponse {
            return AvailableCouponResponse(
                coupons = result.coupons.map { AvailableCouponItem.from(it) }
            )
        }
    }
}

data class AvailableCouponItem(
    val id: Long,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    val remainingQuantity: Int,
    val totalQuantity: Int,
    val issuePeriod: IssuePeriod,
    val validityDays: Int
) {
    companion object {
        fun from(result: AvailableCouponItemDto): AvailableCouponItem {
            return AvailableCouponItem(
                id = result.id,
                couponName = result.couponName,
                description = result.description,
                discountRate = result.discountRate,
                remainingQuantity = result.remainingQuantity,
                totalQuantity = result.totalQuantity,
                issuePeriod = IssuePeriod.from(result.issuePeriod),
                validityDays = result.validityDays
            )
        }
    }
}