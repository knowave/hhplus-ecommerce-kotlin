package com.hhplus.ecommerce.presentation.payment.dto

import com.hhplus.ecommerce.application.payment.dto.RetryTransmissionResult

/**
 * 데이터 전송 재시도 응답 DTO
 * POST /api/data-transmissions/{transmissionId}/retry
 */
data class RetryTransmissionResponse(
    val transmissionId: Long,
    val status: String,
    val retriedAt: String,
    val attempts: Int
) {
    companion object {
        fun from(result: RetryTransmissionResult): RetryTransmissionResponse {
            return RetryTransmissionResponse(
                transmissionId = result.transmissionId,
                status = result.status,
                retriedAt = result.retriedAt,
                attempts = result.attempts
            )
        }
    }
}
