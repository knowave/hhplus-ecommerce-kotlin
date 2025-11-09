package com.hhplus.ecommerce.presentation.cart.dto

import com.hhplus.ecommerce.application.cart.dto.CartItemResult
import java.util.UUID

data class CartItemResponse(
    val cartItemId: UUID,
    val productId: UUID,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val stock: Int,
    val isAvailable: Boolean,
    val addedAt: String
) {
    companion object {
        fun from(result: CartItemResult): CartItemResponse {
            return CartItemResponse(
                cartItemId = result.cartItemId,
                productId = result.productId,
                productName = result.productName,
                price = result.price,
                quantity = result.quantity,
                subtotal = result.subtotal,
                stock = result.stock,
                isAvailable = result.isAvailable,
                addedAt = result.addedAt
            )
        }
    }
}