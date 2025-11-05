package com.hhplus.ecommerce.application.order.dto

data class OrderDetailResult(
    val orderId: Long,
    val userId: Long,
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