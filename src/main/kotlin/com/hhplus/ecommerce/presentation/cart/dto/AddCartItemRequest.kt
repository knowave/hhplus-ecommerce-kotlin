package com.hhplus.ecommerce.presentation.cart.dto

/**
 * 장바구니 상품 추가 요청 DTO
 * POST /api/carts/{userId}/items 엔드포인트의 요청 모델
 */
data class AddCartItemRequest(
    val productId: Long,
    val quantity: Int
)