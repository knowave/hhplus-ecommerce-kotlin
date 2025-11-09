package com.hhplus.ecommerce.application.payment.dto

data class TransmissionDetailResult(
    val transmissionId: Long,
    val orderId: Long,
    val orderNumber: String,
    val status: String,
    val attempts: Int,
    val maxAttempts: Int,
    val createdAt: String,
    val sentAt: String?,
    val nextRetryAt: String?,
    val errorMessage: String?
)
