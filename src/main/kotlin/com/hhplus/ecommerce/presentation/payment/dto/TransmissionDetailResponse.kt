package com.hhplus.ecommerce.presentation.payment.dto

import com.hhplus.ecommerce.application.payment.dto.TransmissionDetailResult

/**
 * 데이터 전송 상태 조회 응답 DTO
 * GET /api/data-transmissions/{transmissionId}
 */
data class TransmissionDetailResponse(
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
) {
    companion object {
        fun from(result: TransmissionDetailResult): TransmissionDetailResponse {
            return TransmissionDetailResponse(
                transmissionId = result.transmissionId,
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                status = result.status,
                attempts = result.attempts,
                maxAttempts = result.maxAttempts,
                createdAt = result.createdAt,
                sentAt = result.sentAt,
                nextRetryAt = result.nextRetryAt,
                errorMessage = result.errorMessage
            )
        }
    }
}
