package com.hhplus.ecommerce.domain.order.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import com.hhplus.ecommerce.common.exception.OrderAlreadyCancelledException
import com.hhplus.ecommerce.common.exception.OrderNotRefundableException
import com.hhplus.ecommerce.common.exception.PaymentFailedException
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "orders", // order table은 SQL 예약어로 인해 orders로 진행
    indexes = [
        // 마이페이지: 사용자별 주문 내역 (가장 중요!)
        Index(name = "idx_order_user_id", columnList = "user_id"),

        // 마이페이지: 최신 주문 순 정렬
        Index(name = "idx_order_user_created", columnList = "user_id, created_at DESC"),

        // 주문 번호로 조회 (주문 상세, 배송 조회 등)
        Index(name = "idx_order_number", columnList = "order_number", unique = true),

        // 주문 상태별 필터 (관리자, 통계)
        Index(name = "idx_order_status", columnList = "status"),

        // 사용자별 상태 필터 (마이페이지에서 "배송 중", "완료" 등 필터)
        Index(name = "idx_order_user_status", columnList = "user_id, status"),

        // 쿠폰 사용 내역 조회
        Index(name = "idx_order_coupon", columnList = "applied_coupon_id")
    ]
)
class Order(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Column(nullable = false, unique = true, length = 50)
    val orderNumber: String,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val items: MutableList<OrderItem> = mutableListOf(),

    @Column(nullable = false)
    val totalAmount: Long,

    @Column(nullable = false)
    val discountAmount: Long,

    @Column(nullable = false)
    val finalAmount: Long,

    @Column(columnDefinition = "BINARY(16)")
    val appliedCouponId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus
) : BaseEntity() {
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
    }

    /**
     * 결제 완료 처리
     * PENDING → PAID
     */
    fun markAsPaid() {
        require(status == OrderStatus.PENDING) {
            throw PaymentFailedException("Only PENDING orders can be marked as PAID. Current status: $status")
        }
        status = OrderStatus.PAID
    }

    /**
     * 주문 취소 처리
     * PENDING → CANCELLED
     */
    fun cancel() {
        require(status == OrderStatus.PENDING) {
            throw OrderAlreadyCancelledException(status)
        }
        status = OrderStatus.CANCELLED
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

    /**
     * 환불 처리
     * PAID → REFUNDED
     */
    fun refund() {
        require(status == OrderStatus.PAID) {
            throw OrderNotRefundableException(status)
        }
        status = OrderStatus.REFUNDED
    }

    /**
     * 환불된 주문인지 확인
     */
    fun isRefunded(): Boolean {
        return status == OrderStatus.REFUNDED
    }
}

/**
 * 주문 상태
 */
enum class OrderStatus {
    PENDING,    // 결제 대기
    PAID,       // 결제 완료
    CANCELLED,  // 주문 취소 (결제 전)
    REFUNDED    // 환불 완료 (결제 후 취소)
}
