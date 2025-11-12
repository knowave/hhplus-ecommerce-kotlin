package com.hhplus.ecommerce.application.payment.dto

import java.util.UUID

data class OrderPaymentResult(
    val orderId: UUID,
    val orderNumber: String,
    val payment: PaymentInfoResult?
)

data class PaymentInfoResult(
    val paymentId: UUID,
    val amount: Long,
    val status: String,
    val paidAt: String
)
