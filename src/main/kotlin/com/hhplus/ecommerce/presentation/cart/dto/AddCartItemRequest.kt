package com.hhplus.ecommerce.presentation.cart.dto

import java.util.UUID

data class AddCartItemRequest(
    val productId: UUID,
    val quantity: Int
)