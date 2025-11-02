package com.hhplus.ecommerce.domains.coupon.dto

import com.hhplus.ecommerce.domains.coupon.CouponStatus

data class UserCoupon(
    val id: Long,
    val userId: Long,
    val couponId: Long,
    var status: CouponStatus,
    val issuedAt: String,
    val expiresAt: String,
    var usedAt: String? = null
)