package com.hhplus.ecommerce.model.order

/**
 * 주문 아이템 도메인 모델
 *
 * 비즈니스 규칙:
 * 1. 수량은 1 이상이어야 함
 * 2. 단가는 0 이상이어야 함
 * 3. 소계 = 단가 × 수량
 */
data class OrderItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long
) {
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