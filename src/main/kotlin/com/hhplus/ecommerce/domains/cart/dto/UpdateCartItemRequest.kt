package com.hhplus.ecommerce.domains.cart.dto

/**
 * 장바구니 아이템 수량 변경 요청 DTO
 * PATCH /api/carts/{userId}/items/{cartItemId} 엔드포인트의 요청 모델
 */
data class UpdateCartItemRequest(
    val quantity: Int
)