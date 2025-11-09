package com.hhplus.ecommerce.presentation.cart.dto

import com.hhplus.ecommerce.application.cart.dto.CartSummaryResult

data class CartSummary(
    val totalItems: Int,         // 총 아이템 종류 수
    val totalQuantity: Int,      // 총 수량
    val totalAmount: Long,       // 총 금액
    val availableAmount: Long,   // 구매 가능한 상품 금액
    val unavailableCount: Int    // 품절 상품 수
) {
    companion object {
        fun from(result: CartSummaryResult): CartSummary {
            return CartSummary(
                totalItems = result.totalItems,
                totalQuantity = result.totalQuantity,
                totalAmount = result.totalAmount,
                availableAmount = result.availableAmount,
                unavailableCount = result.unavailableCount
            )
        }
    }
}