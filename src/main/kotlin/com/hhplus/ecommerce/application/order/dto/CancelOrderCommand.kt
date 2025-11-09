package com.hhplus.ecommerce.application.order.dto

import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest

data class CancelOrderCommand(
    val userId: Long,
    val reason: String? = null
) {
    companion object {
        fun command(request: CancelOrderRequest): CancelOrderCommand {
            return CancelOrderCommand(
                userId = request.userId,
                reason = request.reason
            )
        }
    }
}
