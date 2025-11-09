package com.hhplus.ecommerce.presentation.cart.dto

import com.hhplus.ecommerce.application.cart.dto.UpdateCartItemResult

data class UpdateCartItemResponse(
    val cartItemId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val updatedAt: String
) {
    companion object {
        fun from(result: UpdateCartItemResult): UpdateCartItemResponse {
            return UpdateCartItemResponse(
                cartItemId = result.cartItemId,
                productId = result.productId,
                productName = result.productName,
                price = result.price,
                quantity = result.quantity,
                subtotal = result.subtotal,
                updatedAt = result.updatedAt
            )
        }
    }
}