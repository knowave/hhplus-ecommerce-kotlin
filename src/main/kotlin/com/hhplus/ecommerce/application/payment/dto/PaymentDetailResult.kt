package com.hhplus.ecommerce.application.payment.dto

import java.util.UUID

data class PaymentDetailResult(
    val paymentId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val userId: UUID,
    val amount: Long,
    val paymentStatus: String,
    val paidAt: String,
    val dataTransmission: DataTransmissionDetailInfoResult
)

data class DataTransmissionDetailInfoResult(
    val transmissionId: String,
    val status: String,
    val sentAt: String?,
    val attempts: Int
)