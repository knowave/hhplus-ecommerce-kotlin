package com.hhplus.ecommerce.model.order

import java.time.LocalDateTime

/**
 * 주문 도메인 모델
 */
data class Order(
    val orderId: Long,
    val userId: Long,
    val orderNumber: String,
    val items: List<OrderItem>,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val appliedCouponId: Long?,
    var status: OrderStatus,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime
)

/**
 * 주문 상태
 */
enum class OrderStatus {
    PENDING,    // 결제 대기
    PAID,       // 결제 완료
    CANCELLED   // 주문 취소
}
