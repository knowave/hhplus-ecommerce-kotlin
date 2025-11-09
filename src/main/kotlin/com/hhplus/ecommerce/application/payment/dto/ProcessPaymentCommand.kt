package com.hhplus.ecommerce.application.payment.dto

import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentRequest

data class ProcessPaymentCommand(
    val userId: Long
) {
    companion object {
        fun command(result: ProcessPaymentRequest): ProcessPaymentCommand {
            return ProcessPaymentCommand(
                userId = result.userId
            )
        }
    }
}