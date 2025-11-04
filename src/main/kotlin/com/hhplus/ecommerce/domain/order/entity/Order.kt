package com.hhplus.ecommerce.model.order

import java.time.LocalDateTime

/**
 * 주문 도메인 모델
 *
 * 비즈니스 규칙:
 * 1. 주문 생성 시 PENDING 상태로 시작
 * 2. PENDING 상태에서만 PAID 또는 CANCELLED로 전환 가능
 * 3. PAID, CANCELLED 상태는 최종 상태 (변경 불가)
 */
data class Order(
    val id: Long,
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
) {
    /**
     * 주문 금액 검증
     */
    init {
        require(totalAmount >= 0) { "Total amount must be non-negative" }
        require(discountAmount >= 0) { "Discount amount must be non-negative" }
        require(finalAmount >= 0) { "Final amount must be non-negative" }
        require(finalAmount == totalAmount - discountAmount) {
            "Final amount must equal total amount minus discount amount"
        }
        require(items.isNotEmpty()) { "Order must have at least one item" }
    }

    /**
     * 결제 완료 처리
     * PENDING → PAID
     */
    fun markAsPaid() {
        require(status == OrderStatus.PENDING) {
            "Only PENDING orders can be marked as PAID. Current status: $status"
        }
        status = OrderStatus.PAID
        updatedAt = LocalDateTime.now()
    }

    /**
     * 주문 취소 처리
     * PENDING → CANCELLED
     */
    fun cancel() {
        require(status == OrderStatus.PENDING) {
            "Only PENDING orders can be cancelled. Current status: $status"
        }
        status = OrderStatus.CANCELLED
        updatedAt = LocalDateTime.now()
    }

    /**
     * 주문 취소 가능 여부
     */
    fun isCancellable(): Boolean {
        return status == OrderStatus.PENDING
    }

    /**
     * 결제 대기 중인지 확인
     */
    fun isPending(): Boolean {
        return status == OrderStatus.PENDING
    }

    /**
     * 결제 완료 상태인지 확인
     */
    fun isPaid(): Boolean {
        return status == OrderStatus.PAID
    }

    /**
     * 취소된 주문인지 확인
     */
    fun isCancelled(): Boolean {
        return status == OrderStatus.CANCELLED
    }
}

/**
 * 주문 상태
 */
enum class OrderStatus {
    PENDING,    // 결제 대기
    PAID,       // 결제 완료
    CANCELLED   // 주문 취소
}
