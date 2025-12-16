package com.hhplus.ecommerce.application.payment.dto

import java.time.LocalDateTime
import java.util.UUID

data class PaymentTransactionData(
    val paymentId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val userId: UUID,
    val amount: Long,
    val paymentStatus: String,
    val orderStatus: String,
    val previousBalance: Long,
    val currentBalance: Long,
    val paidAt: LocalDateTime
)