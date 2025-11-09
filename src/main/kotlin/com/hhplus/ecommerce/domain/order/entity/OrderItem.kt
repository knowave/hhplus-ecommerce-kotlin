package com.hhplus.ecommerce.domain.order.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "order_items")
class OrderItem(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val productId: UUID,

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @Column(nullable = false, length = 255)
    val productName: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val unitPrice: Long,

    @Column(nullable = false)
    val subtotal: Long
) : BaseEntity() {
    /**
     * 주문 아이템 검증
     */
    init {
        require(quantity >= 1) { "Quantity must be at least 1" }
        require(unitPrice >= 0) { "Unit price must be non-negative" }
        require(subtotal >= 0) { "Subtotal must be non-negative" }
        require(subtotal == unitPrice * quantity) {
            "Subtotal must equal unit price times quantity"
        }
    }

    /**
     * 주문 아이템의 총 금액 계산
     */
    fun calculateTotalPrice(): Long {
        return unitPrice * quantity
    }
}