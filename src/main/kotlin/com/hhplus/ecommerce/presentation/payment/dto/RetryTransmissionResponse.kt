package com.hhplus.ecommerce.presentation.payment.dto

/**
 * 데이터 전송 재시도 응답 DTO
 * POST /api/data-transmissions/{transmissionId}/retry
 */
data class RetryTransmissionResponse(
    val transmissionId: Long,
    val status: String,
    val retriedAt: String,
    val attempts: Int
)
