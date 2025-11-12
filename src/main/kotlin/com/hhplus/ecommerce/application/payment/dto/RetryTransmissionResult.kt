package com.hhplus.ecommerce.application.payment.dto

import java.util.UUID

data class RetryTransmissionResult(
    val transmissionId: UUID,
    val status: String,
    val retriedAt: String,
    val attempts: Int
)
