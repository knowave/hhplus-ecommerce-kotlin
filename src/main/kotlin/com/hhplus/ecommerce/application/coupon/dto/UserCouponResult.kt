package com.hhplus.ecommerce.application.coupon.dto

import com.hhplus.ecommerce.domain.coupon.CouponStatus

data class UserCouponResult(
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
)