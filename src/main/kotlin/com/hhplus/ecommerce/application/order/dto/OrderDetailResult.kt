package com.hhplus.ecommerce.application.order.dto

import java.util.UUID

data class OrderDetailResult(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val items: List<OrderItemResult>,
    val pricing: PricingInfoDto,
    val status: String,
    val payment: PaymentInfoDto?,
    val createdAt: String,
    val updatedAt: String
)

data class PaymentInfoDto(
    val paidAmount: Long,
    val paidAt: String
)