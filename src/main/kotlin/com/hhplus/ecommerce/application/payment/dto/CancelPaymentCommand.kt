package com.hhplus.ecommerce.application.payment.dto

import com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentRequest

data class CancelPaymentCommand(
    val userId: Long
) {
    companion object {
        fun from(result: CancelPaymentRequest): CancelPaymentCommand {
            return CancelPaymentCommand(
                userId = result.userId
            )
        }
    }
}