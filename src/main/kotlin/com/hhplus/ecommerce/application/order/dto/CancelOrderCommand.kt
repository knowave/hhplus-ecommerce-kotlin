package com.hhplus.ecommerce.application.order.dto

import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import java.util.UUID

data class CancelOrderCommand(
    val userId: UUID,
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
