package com.hhplus.ecommerce.application.shipping.dto

import com.hhplus.ecommerce.presentation.shipping.dto.UpdateShippingStatusRequest
import java.time.LocalDateTime

data class UpdateShippingStatusCommand(
    val status: String,
    val deliveredAt: LocalDateTime?
) {
    companion object {
        fun command(request: UpdateShippingStatusRequest): UpdateShippingStatusCommand {
            return UpdateShippingStatusCommand(
                status = request.status,
                deliveredAt = request.deliveredAt
            )
        }
    }
}