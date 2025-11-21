package com.hhplus.ecommerce.application.order.dto

import java.util.UUID

data class OrderCreationData(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val items: List<OrderItemResult>,
    val pricing: PricingInfoDto,
    val status: String,
    val createdAt: String,
    val productIds: List<UUID>
)
