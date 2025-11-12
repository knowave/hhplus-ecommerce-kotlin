package com.hhplus.ecommerce.presentation.order.dto

import java.util.UUID

/**
 * 주문 취소 요청 DTO
 */
data class CancelOrderRequest(
    val userId: UUID,
    val reason: String? = null
)