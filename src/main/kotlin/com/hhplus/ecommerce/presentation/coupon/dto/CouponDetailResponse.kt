package com.hhplus.ecommerce.presentation.coupon.dto

import com.hhplus.ecommerce.application.coupon.dto.CouponDetailResult

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
) {
    companion object {
        fun from(result: CouponDetailResult): CouponDetailResponse {
            return CouponDetailResponse(
                id = result.id,
                couponName = result.couponName,
                description =  result.description,
                discountRate = result.discountRate,
                totalQuantity = result.totalQuantity,
                issuedQuantity = result.issuedQuantity,
                remainingQuantity = result.remainingQuantity,
                issuePeriod = IssuePeriod.from(result.issuePeriod),
                validityDays = result.validityDays,
                isAvailable = result.isAvailable,
                createdAt = result.createdAt
            )
        }
    }
}