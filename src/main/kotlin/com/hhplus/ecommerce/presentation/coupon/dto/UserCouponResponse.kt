package com.hhplus.ecommerce.presentation.coupon.dto

import com.hhplus.ecommerce.application.coupon.dto.UserCouponResult
import com.hhplus.ecommerce.domain.coupon.CouponStatus

data class UserCouponResponse(
    val id: Long,
    val userId: Long,
    val couponId: Long,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    var status: CouponStatus,
    val issuedAt: String,
    val expiresAt: String,
    var usedAt: String? = null,
    val isExpired: Boolean,
    val canUse: Boolean
) {
    companion object {
        fun from(result: UserCouponResult): UserCouponResponse {
            return UserCouponResponse(
                id = result.id,
                userId = result.userId,
                couponId = result.couponId,
                couponName = result.couponName,
                description = result.description,
                discountRate = result.discountRate,
                status = result.status,
                issuedAt = result.issuedAt,
                expiresAt = result.expiresAt,
                usedAt = result.usedAt,
                isExpired = result.isExpired,
                canUse = result.canUse
            )
        }
    }
}