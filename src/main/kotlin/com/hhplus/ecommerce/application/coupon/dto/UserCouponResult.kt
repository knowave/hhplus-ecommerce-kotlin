package com.hhplus.ecommerce.application.coupon.dto

import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import java.util.UUID

data class UserCouponResult(
    val id: UUID,
    val userId: UUID,
    val couponId: UUID,
    val couponName: String,
    val description: String,
    val discountRate: Int,
    var status: CouponStatus,
    val issuedAt: String,
    val expiresAt: String,
    var usedAt: String? = null,
    val isExpired: Boolean,
    val canUse: Boolean
)