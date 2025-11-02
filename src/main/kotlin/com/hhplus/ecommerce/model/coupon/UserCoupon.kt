package com.hhplus.ecommerce.model.coupon

import com.hhplus.ecommerce.infrastructure.coupon.CouponStatus

data class UserCoupon(
    val id: Long,
    val userId: Long,
    val couponId: Long,
    var status: CouponStatus,
    val issuedAt: String,
    val expiresAt: String,
    var usedAt: String? = null
)