package com.hhplus.ecommerce.application.payment.dto

data class OrderPaymentResult(
    val orderId: Long,
    val orderNumber: String,
    val payment: PaymentInfoResult?
)

data class PaymentInfoResult(
    val paymentId: Long,
    val amount: Long,
    val status: String,
    val paidAt: String
)
