package com.hhplus.ecommerce.application.payment.dto

data class PaymentDetailResult(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
    val amount: Long,
    val paymentStatus: String,
    val paidAt: String,
    val dataTransmission: DataTransmissionDetailInfoResult
)

data class DataTransmissionDetailInfoResult(
    val transmissionId: Long,
    val status: String,
    val sentAt: String?,
    val attempts: Int
)