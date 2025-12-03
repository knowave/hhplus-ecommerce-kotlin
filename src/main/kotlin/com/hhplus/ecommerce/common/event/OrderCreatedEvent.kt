package com.hhplus.ecommerce.common.event

import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 생성 이벤트
 *
 * 주문이 생성되면 발행되는 이벤트로, 비동기 작업을 트리거합니다.
 * - 카트 삭제
 * - 상품 랭킹 업데이트
 * - 알림 발송 (향후 추가 가능)
 * - 통계 업데이트 (향후 추가 가능)
 */
data class OrderCreatedEvent(
    val orderId: UUID,
    val userId: UUID,
    val productIds: List<UUID>,
    val items: List<OrderItemInfo>,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 주문 항목 정보
 *
 * 이벤트에서 필요한 최소한의 주문 항목 정보만 포함
 */
data class OrderItemInfo(
    val productId: UUID,
    val quantity: Int
)
