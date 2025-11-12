package com.hhplus.ecommerce.presentation.cart.dto

import com.hhplus.ecommerce.application.cart.dto.AddCartItemResult
import java.util.UUID

data class AddCartItemResponse(
    val cartItemId: UUID,
    val productId: UUID,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val addedAt: String
) {
    companion object {
        fun from(result: AddCartItemResult): AddCartItemResponse {
            return AddCartItemResponse(
                cartItemId = result.cartItemId,
                productId = result.productId,
                productName = result.productName,
                price = result.price,
                quantity = result.quantity,
                subtotal = result.subtotal,
                addedAt = result.addedAt
            )
        }
    }
}