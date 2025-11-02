package com.hhplus.ecommerce.presentation.payment.dto

/**
 * 결제 처리 응답 DTO
 */
data class ProcessPaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
    val amount: Long,
    val paymentStatus: String,
    val orderStatus: String,
    val balance: BalanceInfo,
    val dataTransmission: DataTransmissionInfo,
    val paidAt: String
)

data class BalanceInfo(
    val previousBalance: Long,
    val paidAmount: Long,
    val remainingBalance: Long
)

data class DataTransmissionInfo(
    val transmissionId: Long,
    val status: String,
    val scheduledAt: String
)
