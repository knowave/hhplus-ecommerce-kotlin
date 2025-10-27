package com.hhplus.ecommerce.domain.coupon.dto

import com.hhplus.ecommerce.domain.coupon.entity.CouponStatus
import java.time.LocalDateTime

data class UserCouponResponseDto(
    val userCouponId: String,
    val couponName: String,
    val discountRate: Int,
    val status: String,         // "AVAILABLE" | "USED" | "EXPIRED"
    val expiresAt: String       // ISO-8601 문자열 (예: 2025-12-31T23:59:59)
) {
    constructor(
        userCouponId: String,
        couponName: String,
        discountRate: Int,
        status: CouponStatus,  // Enum 타입
        expiresAt: LocalDateTime  // LocalDateTime 타입
    ) : this(
        userCouponId = userCouponId,
        couponName = couponName,
        discountRate = discountRate,
        status = status.name,  // String으로 변환
        expiresAt = expiresAt.toString()  // String으로 변환
    )
}