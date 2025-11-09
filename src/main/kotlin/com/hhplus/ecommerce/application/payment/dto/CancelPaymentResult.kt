package com.hhplus.ecommerce.application.payment.dto

data class CancelPaymentResult(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
    val refundedAmount: Long,
    val paymentStatus: String,
    val orderStatus: String,
    val balance: RefundBalanceInfoResult,
    val cancelledAt: String
)

data class RefundBalanceInfoResult(
    val previousBalance: Long,
    val refundedAmount: Long,
    val currentBalance: Long
)
