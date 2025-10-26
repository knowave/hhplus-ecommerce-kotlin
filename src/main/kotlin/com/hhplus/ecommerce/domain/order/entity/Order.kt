package com.hhplus.ecommerce.domain.order.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_user_status", columnList = "user_id, status"),
        Index(name = "idx_created_at", columnList = "created_at")
    ]
)
class Order(
    id: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    val discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "final_amount", nullable = false, precision = 10, scale = 2)
    val finalAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf()
) : CustomBaseEntity(id) {

    fun markAsPaid() {
        require(status == OrderStatus.PENDING) { "PENDING 상태의 주문만 결제 완료 처리할 수 있습니다." }
        status = OrderStatus.PAID
        paidAt = LocalDateTime.now()
    }

    fun cancel() {
        require(status == OrderStatus.PENDING) { "PENDING 상태의 주문만 취소할 수 있습니다." }
        status = OrderStatus.CANCELLED
    }

    fun addItem(orderItem: OrderItem) {
        items.add(orderItem)
    }
}
