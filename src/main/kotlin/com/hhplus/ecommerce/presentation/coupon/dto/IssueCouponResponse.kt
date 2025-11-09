package com.hhplus.ecommerce.presentation.coupon.dto

import com.hhplus.ecommerce.application.coupon.dto.IssueCouponResult

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
) {
    companion object {
        fun from(result: IssueCouponResult): IssueCouponResponse {
            return IssueCouponResponse(
                userCouponId = result.userCouponId,
                userId = result.userId,
                couponId = result.couponId,
                couponName = result.couponName,
                discountRate = result.discountRate,
                status = result.status,
                issuedAt = result.issuedAt,
                expiresAt = result.expiresAt,
                remainingQuantity = result.remainingQuantity,
                totalQuantity = result.totalQuantity
            )
        }
    }
}