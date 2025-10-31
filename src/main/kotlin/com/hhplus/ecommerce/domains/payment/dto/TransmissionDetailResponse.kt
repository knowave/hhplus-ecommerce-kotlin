package com.hhplus.ecommerce.domains.payment.dto

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
)
