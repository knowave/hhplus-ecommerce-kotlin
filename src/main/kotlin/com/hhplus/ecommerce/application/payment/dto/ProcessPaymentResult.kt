package com.hhplus.ecommerce.application.payment.dto

data class ProcessPaymentResult(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
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
    val transmissionId: Long,
    val status: String,
    val scheduledAt: String
)