package com.hhplus.ecommerce.presentation.payment.dto

import java.util.UUID

/**
 * 결제 처리 요청 DTO
 * POST /api/orders/{orderId}/payment
 */
data class ProcessPaymentRequest(
    val userId: UUID
)
