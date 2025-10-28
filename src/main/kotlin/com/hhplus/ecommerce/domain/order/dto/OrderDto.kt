package com.hhplus.ecommerce.domain.order.dto

import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

// Request DTOs
data class CreateOrderRequestDto(
    val userId: String,
    val items: List<OrderItemRequestDto>,
    val couponId: String? = null
)

data class OrderItemRequestDto(
    val productId: String,
    val quantity: Int
)

// Response DTOs
data class CreateOrderResponseDto(
    val orderId: String,
    val items: List<OrderItemResponseDto>,
    val totalAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val finalAmount: BigDecimal,
    val status: OrderStatus
)

data class OrderItemResponseDto(
    val productId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

data class OrderResponseDto(
    val orderId: String,
    val userId: String,
    val items: List<OrderItemResponseDto>,
    val totalAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val finalAmount: BigDecimal,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
    val paidAt: LocalDateTime?
)