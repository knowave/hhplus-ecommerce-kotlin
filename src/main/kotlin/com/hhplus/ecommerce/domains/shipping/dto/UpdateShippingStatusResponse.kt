package com.hhplus.ecommerce.domains.shipping.dto

import java.time.LocalDateTime

/**
 * 배송 상태 변경 응답
 */
data class UpdateShippingStatusResponse(
    val shippingId: Long,
    val orderId: Long,
    val status: String,
    val deliveredAt: LocalDateTime?,
    val updatedAt: LocalDateTime
)
