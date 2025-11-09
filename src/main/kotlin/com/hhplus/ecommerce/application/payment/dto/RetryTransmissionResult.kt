package com.hhplus.ecommerce.application.payment.dto

data class RetryTransmissionResult(
    val transmissionId: Long,
    val status: String,
    val retriedAt: String,
    val attempts: Int
)
