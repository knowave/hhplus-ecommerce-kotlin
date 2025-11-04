package com.hhplus.ecommerce.domain.payment.entity

import java.time.LocalDateTime

/**
 * 데이터 전송 도메인 모델 (Outbox Pattern)
 */
data class DataTransmission(
    val transmissionId: Long,
    val orderId: Long,
    var status: TransmissionStatus,
    var attempts: Int = 0,
    val maxAttempts: Int = 3,
    val createdAt: LocalDateTime,
    var sentAt: LocalDateTime? = null,
    var nextRetryAt: LocalDateTime? = null,
    var errorMessage: String? = null
)

/**
 * 전송 상태
 */
enum class TransmissionStatus {
    PENDING,    // 전송 대기
    SUCCESS,    // 전송 성공
    FAILED      // 전송 실패 (최대 재시도 초과)
}
