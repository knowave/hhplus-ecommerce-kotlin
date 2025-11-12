package com.hhplus.ecommerce.presentation.cart.dto

import com.hhplus.ecommerce.application.cart.dto.CartResult
import com.hhplus.ecommerce.domain.cart.entity.CartItem
import java.util.UUID

data class CartResponse(
    val userId: UUID,
    val items: List<CartItemResponse>,
    val summary: CartSummary
) {
    companion object {
        fun from(result: CartResult): CartResponse {
            return CartResponse(
                userId = result.userId,
                items = result.items.map { CartItemResponse.from(it) },
                summary = CartSummary.from(result.summary)
            )
        }
    }
}