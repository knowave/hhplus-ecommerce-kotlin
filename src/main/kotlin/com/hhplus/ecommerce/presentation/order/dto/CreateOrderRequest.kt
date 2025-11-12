package com.hhplus.ecommerce.presentation.order.dto

import java.util.UUID

/**
 * 주문 생성 요청 DTO
 */
data class CreateOrderRequest(
    val userId: UUID,
    val items: List<OrderItemRequest>,
    val couponId: UUID? = null
)

data class OrderItemRequest(
    val productId: UUID,
    val quantity: Int
)
