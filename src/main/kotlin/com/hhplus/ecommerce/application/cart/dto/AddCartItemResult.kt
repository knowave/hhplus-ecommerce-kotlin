package com.hhplus.ecommerce.application.cart.dto

import java.util.UUID

data class AddCartItemResult(
    val cartItemId: UUID,
    val productId: UUID,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val addedAt: String
)
