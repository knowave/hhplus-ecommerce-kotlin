package com.hhplus.ecommerce.domains.payment.dto

/**
 * 주문별 결제 내역 조회 응답 DTO
 * GET /api/orders/{orderId}/payment
 */
data class OrderPaymentResponse(
    val orderId: Long,
    val orderNumber: String,
    val payment: PaymentInfo?
)

data class PaymentInfo(
    val paymentId: Long,
    val amount: Long,
    val status: String,
    val paidAt: String
)
