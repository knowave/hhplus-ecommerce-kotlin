package com.hhplus.ecommerce.presentation.cart.dto

/**
 * 장바구니 요약 정보
 * 전체 수량, 금액 등의 집계 정보
 */
data class CartSummary(
    val totalItems: Int,         // 총 아이템 종류 수
    val totalQuantity: Int,      // 총 수량
    val totalAmount: Long,       // 총 금액
    val availableAmount: Long,   // 구매 가능한 상품 금액
    val unavailableCount: Int    // 품절 상품 수
)