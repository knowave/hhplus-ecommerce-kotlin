package com.hhplus.ecommerce.application.cart.dto

import com.hhplus.ecommerce.presentation.cart.dto.UpdateCartItemRequest

data class UpdateCartItemCommand(
    val quantity: Int
) {
    companion object {
        fun command(request: UpdateCartItemRequest): UpdateCartItemCommand {
            return UpdateCartItemCommand(
                quantity = request.quantity
            )
        }
    }
}
