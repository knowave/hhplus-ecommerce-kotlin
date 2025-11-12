package com.hhplus.ecommerce.application.payment.dto

import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentRequest
import java.util.UUID

data class ProcessPaymentCommand(
    val userId: UUID
) {
    companion object {
        fun command(result: ProcessPaymentRequest): ProcessPaymentCommand {
            return ProcessPaymentCommand(
                userId = result.userId
            )
        }
    }
}