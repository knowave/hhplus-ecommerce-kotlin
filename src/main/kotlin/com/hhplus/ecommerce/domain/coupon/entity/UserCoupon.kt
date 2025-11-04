package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.domain.coupon.CouponStatus

data class UserCoupon(
    val id: Long,
    val userId: Long,
    val couponId: Long,
    var status: CouponStatus,
    val issuedAt: String,
    val expiresAt: String,
    var usedAt: String? = null
)