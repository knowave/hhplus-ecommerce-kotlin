package com.hhplus.ecommerce.presentation.cart.dto

/**
 * 장바구니 아이템 응답 DTO
 * API 응답에서 개별 아이템 정보를 표현
 */
data class CartItemResponse(
    val cartItemId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val stock: Int,
    val isAvailable: Boolean,
    val addedAt: String
)