package com.hhplus.ecommerce.domains.cart.dto

/**
 * 장바구니 아이템 수량 변경 응답 DTO
 * PATCH /api/carts/{userId}/items/{cartItemId} 엔드포인트의 응답 모델
 */
data class UpdateCartItemResponse(
    val cartItemId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val updatedAt: String
)