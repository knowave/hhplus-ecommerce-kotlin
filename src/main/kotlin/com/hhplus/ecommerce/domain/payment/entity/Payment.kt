package com.hhplus.ecommerce.domain.payment.entity

import java.time.LocalDateTime

/**
 * 결제 도메인 모델
 */
data class Payment(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val status: PaymentStatus,
    val paidAt: LocalDateTime
)

/**
 * 결제 상태
 */
enum class PaymentStatus {
    SUCCESS,    // 결제 성공
    FAILED,     // 결제 실패
    CANCELLED   // 결제 취소
}
