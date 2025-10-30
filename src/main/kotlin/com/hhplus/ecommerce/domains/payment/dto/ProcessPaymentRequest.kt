package com.hhplus.ecommerce.domains.payment.dto

/**
 * 결제 처리 요청 DTO
 * POST /api/orders/{orderId}/payment
 */
data class ProcessPaymentRequest(
    val userId: Long
)
