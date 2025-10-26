package com.hhplus.ecommerce.domain.order.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(name = "idx_order_id", columnList = "order_id"),
        Index(name = "idx_product_id", columnList = "product_id")
    ]
)
class OrderItem(
    id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @Column(name = "product_id", nullable = false)
    val productId: String,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    val unitPrice: BigDecimal,

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    val subtotal: BigDecimal
) : CustomBaseEntity(id) {

    init {
        require(quantity > 0) { "주문 수량은 0보다 커야 합니다." }
        require(unitPrice >= BigDecimal.ZERO) { "단가는 0 이상이어야 합니다." }
        require(subtotal == unitPrice.multiply(BigDecimal(quantity))) {
            "소계는 단가 x 수량과 일치해야 합니다."
        }
    }
}
