package com.hhplus.ecommerce.presentation.payment.dto

import java.util.UUID

/**
 * 결제 취소 요청 DTO
 */
data class CancelPaymentRequest(
    val userId: UUID
)