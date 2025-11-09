package com.hhplus.ecommerce.domain.payment.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "payment",
    indexes = [
        // 주문별 결제 정보 조회 (주문 상세에서 결제 정보 확인)
        Index(name = "idx_payment_order_id", columnList = "order_id"),

        // 사용자별 결제 내역 (마이페이지 결제 내역)
        Index(name = "idx_payment_user_id", columnList = "user_id"),

        // 사용자별 최신 결제 순
        Index(name = "idx_payment_user_paid", columnList = "user_id, paid_at DESC"),

        // 결제 상태별 필터 (관리자, 통계, 실패 건 재시도)
        Index(name = "idx_payment_status", columnList = "status"),

        // 결제 상태별 시간 순 (실패 건 모니터링)
        Index(name = "idx_payment_status_paid", columnList = "status, paid_at DESC")
    ]
)
class Payment(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val orderId: UUID,

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Column(nullable = false)
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PaymentStatus,

    @Column(nullable = false)
    val paidAt: LocalDateTime
) : BaseEntity()

/**
 * 결제 상태
 */
enum class PaymentStatus {
    SUCCESS,    // 결제 성공
    FAILED,     // 결제 실패
    CANCELLED   // 결제 취소
}
