package com.hhplus.ecommerce.presentation.payment.dto

import com.hhplus.ecommerce.application.payment.dto.*
import java.util.UUID

/**
 * 결제 정보 조회 응답 DTO
 * GET /api/payments/{paymentId}
 */
data class PaymentDetailResponse(
    val paymentId: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val userId: UUID,
    val amount: Long,
    val paymentStatus: String,
    val paidAt: String,
    val dataTransmission: DataTransmissionDetailInfo
) {
    companion object {
        fun from(result: PaymentDetailResult): PaymentDetailResponse {
            return PaymentDetailResponse(
                paymentId = result.paymentId,
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                userId = result.userId,
                amount = result.amount,
                paymentStatus = result.paymentStatus,
                paidAt = result.paidAt,
                dataTransmission = DataTransmissionDetailInfo.from(result.dataTransmission)
            )
        }
    }
}

data class DataTransmissionDetailInfo(
    val transmissionId: String,
    val status: String,
    val sentAt: String?,
    val attempts: Int
) {
    companion object {
        fun from(result: DataTransmissionDetailInfoResult): DataTransmissionDetailInfo {
            return DataTransmissionDetailInfo(
                transmissionId = result.transmissionId,
                status = result.status,
                sentAt = result.sentAt,
                attempts = result.attempts
            )
        }
    }
}
