package com.hhplus.ecommerce.domains.order.dto

/**
 * 주문 취소 요청 DTO
 */
data class CancelOrderRequest(
    val userId: Long,
    val reason: String? = null
)