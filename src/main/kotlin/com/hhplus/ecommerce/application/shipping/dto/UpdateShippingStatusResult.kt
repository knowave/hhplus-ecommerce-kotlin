package com.hhplus.ecommerce.application.shipping.dto

import java.time.LocalDateTime
import java.util.UUID

data class UpdateShippingStatusResult(
    val shippingId: UUID,
    val orderId: UUID,
    val status: String,
    val deliveredAt: LocalDateTime?,
    val updatedAt: LocalDateTime
)
