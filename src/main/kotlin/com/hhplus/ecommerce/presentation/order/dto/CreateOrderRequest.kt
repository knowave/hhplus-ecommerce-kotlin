package com.hhplus.ecommerce.presentation.order.dto

/**
 * 주문 생성 요청 DTO
 */
data class CreateOrderRequest(
    val userId: Long,
    val items: List<OrderItemRequest>,
    val couponId: Long? = null
)

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int
)
