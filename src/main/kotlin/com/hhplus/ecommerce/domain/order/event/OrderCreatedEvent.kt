package com.hhplus.ecommerce.domain.order.event

import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 이벤트
 *
 * 주문이 생성되면 발행되는 이벤트로, 비동기 작업을 트리거합니다.
 * - 카트 삭제
 * - 알림 발송 (향후 추가 가능)
 * - 통계 업데이트 (향후 추가 가능)
 */
data class OrderCreatedEvent(
    val orderId: UUID,
    val userId: UUID,
    val productIds: List<UUID>,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
