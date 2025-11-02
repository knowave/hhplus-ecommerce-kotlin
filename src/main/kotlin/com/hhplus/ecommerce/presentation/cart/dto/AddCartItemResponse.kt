package com.hhplus.ecommerce.presentation.cart.dto

/**
 * 장바구니 상품 추가 응답 DTO
 * POST /api/carts/{userId}/items 엔드포인트의 응답 모델
 */
data class AddCartItemResponse(
    val cartItemId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val addedAt: String
)