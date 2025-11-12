package com.hhplus.ecommerce.application.payment.dto

import com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentRequest
import java.util.UUID

data class CancelPaymentCommand(
    val userId: UUID
) {
    companion object {
        fun from(result: CancelPaymentRequest): CancelPaymentCommand {
            return CancelPaymentCommand(
                userId = result.userId
            )
        }
    }
}