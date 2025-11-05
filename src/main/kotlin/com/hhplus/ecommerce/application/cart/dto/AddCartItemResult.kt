package com.hhplus.ecommerce.application.cart.dto

data class AddCartItemResult(
    val cartItemId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val addedAt: String
)
