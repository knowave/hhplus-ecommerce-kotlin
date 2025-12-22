package com.hhplus.ecommerce.common.event

import java.time.LocalDateTime
import java.util.UUID

/**
 * 쿠폰 발급 이벤트
 *
 * 쿠폰이 발급되면 발행되는 이벤트로, 비동기 작업을 트리거합니다.
 * - 사용자 알림 발송 (쿠폰 발급 완료 알림)
 */
data class CouponIssuedEvent(
    val userCouponId: UUID,
    val userId: UUID,
    val couponId: UUID,
    val couponName: String,
    val discountRate: Int,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val timestamp: LocalDateTime = LocalDateTime.now()
)