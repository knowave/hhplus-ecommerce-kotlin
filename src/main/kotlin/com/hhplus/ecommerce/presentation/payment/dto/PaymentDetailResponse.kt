package com.hhplus.ecommerce.presentation.payment.dto

/**
 * 결제 정보 조회 응답 DTO
 * GET /api/payments/{paymentId}
 */
data class PaymentDetailResponse(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
    val amount: Long,
    val paymentStatus: String,
    val paidAt: String,
    val dataTransmission: DataTransmissionDetailInfo
)

data class DataTransmissionDetailInfo(
    val transmissionId: Long,
    val status: String,
    val sentAt: String?,
    val attempts: Int
)
