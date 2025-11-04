package com.hhplus.ecommerce.domain.shipping

import java.time.LocalDateTime

/**
 * 배송 도메인 모델
 */
data class Shipping(
    val id: Long,
    val orderId: Long,
    val carrier: String,
    val trackingNumber: String,
    val shippingStartAt: LocalDateTime?,
    val estimatedArrivalAt: LocalDateTime,
    val deliveredAt: LocalDateTime?,
    var status: ShippingStatus,
    val isDelayed: Boolean = false,
    val isExpired: Boolean = false,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime
)

/**
 * 배송 상태
 */
enum class ShippingStatus {
    PENDING,      // 배송 대기
    IN_TRANSIT,   // 배송 중
    DELIVERED     // 배송 완료
}
