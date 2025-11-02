package com.hhplus.ecommerce.presentation.cart.dto

/**
 * 장바구니 조회 응답 DTO
 * GET /api/carts/{userId} 엔드포인트의 응답 모델
 */
data class CartResponse(
    val userId: Long,
    val items: List<CartItemResponse>,
    val summary: CartSummary
)