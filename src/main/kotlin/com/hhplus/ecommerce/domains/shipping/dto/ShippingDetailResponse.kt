package com.hhplus.ecommerce.domains.shipping.dto

import java.time.LocalDateTime

/**
 * 배송 조회 응답
 */
data class ShippingDetailResponse(
    val shippingId: Long,
    val orderId: Long,
    val carrier: String,
    val trackingNumber: String,
    val shippingStartAt: LocalDateTime?,
    val estimatedArrivalAt: LocalDateTime,
    val deliveredAt: LocalDateTime?,
    val status: String,
    val isDelayed: Boolean,
    val isExpired: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
