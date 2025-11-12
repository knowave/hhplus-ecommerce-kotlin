package com.hhplus.ecommerce.application.payment.dto

import java.util.UUID

data class CancelPaymentResult(
    val paymentId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val userId: UUID,
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
