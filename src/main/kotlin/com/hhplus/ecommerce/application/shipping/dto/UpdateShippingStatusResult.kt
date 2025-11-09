package com.hhplus.ecommerce.application.shipping.dto

import java.time.LocalDateTime

data class UpdateShippingStatusResult(
    val shippingId: Long,
    val orderId: Long,
    val status: String,
    val deliveredAt: LocalDateTime?,
    val updatedAt: LocalDateTime
)
