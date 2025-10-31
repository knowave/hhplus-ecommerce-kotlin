package com.hhplus.ecommerce.domains.order.dto

/**
 * 주문 아이템 도메인 모델
 */
data class OrderItem(
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long
)