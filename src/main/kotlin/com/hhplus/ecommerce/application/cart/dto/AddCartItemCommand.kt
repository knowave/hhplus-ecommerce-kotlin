package com.hhplus.ecommerce.application.cart.dto

import com.hhplus.ecommerce.presentation.cart.dto.AddCartItemRequest

data class AddCartItemCommand(
    val productId: Long,
    val quantity: Int
) {
    companion object {
        fun command(request: AddCartItemRequest): AddCartItemCommand {
            return AddCartItemCommand(
                productId = request.productId,
                quantity = request.quantity
            )
        }
    }
}