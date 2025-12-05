package com.hhplus.ecommerce.application.payment.dto

import java.util.UUID

data class ProcessPaymentResult(
    val paymentId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val userId: UUID,
    val amount: Long,
    val paymentStatus: String,
    val orderStatus: String,
    val balance: BalanceInfoResult,
    val dataTransmission: DataTransmissionInfoResult,
    val paidAt: String
)

data class BalanceInfoResult(
    val previousBalance: Long,
    val paidAmount: Long,
    val remainingBalance: Long
)

data class DataTransmissionInfoResult(
    val transmissionId: UUID?,
    val status: String,
    val scheduledAt: String
)