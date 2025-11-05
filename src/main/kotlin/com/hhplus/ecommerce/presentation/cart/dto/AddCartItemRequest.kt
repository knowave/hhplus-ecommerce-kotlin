package com.hhplus.ecommerce.presentation.cart.dto

data class AddCartItemRequest(
    val productId: Long,
    val quantity: Int
)