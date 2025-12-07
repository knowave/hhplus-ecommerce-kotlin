package com.hhplus.ecommerce.common.event

import java.time.LocalDateTime
import java.util.UUID

/**
 * 결제 완료 이벤트
 *
 * 결제가 완료되면 발행되는 이벤트로, 비동기 작업을 트리거합니다.
 * - 데이터 플랫폼 전송
 */
data class PaymentCompletedEvent(
    val paymentId: UUID,
    val orderId: UUID,
    val userId: UUID,
    val amount: Long,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

