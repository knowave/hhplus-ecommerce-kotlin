package com.hhplus.ecommerce.application.payment.dto

import java.util.UUID

data class TransmissionDetailResult(
    val transmissionId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val status: String,
    val attempts: Int,
    val maxAttempts: Int,
    val createdAt: String,
    val sentAt: String?,
    val nextRetryAt: String?,
    val errorMessage: String?
)
