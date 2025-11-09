package com.hhplus.ecommerce.presentation.shipping.dto

import java.time.LocalDateTime

/**
 * 배송 상태 변경 요청
 */
data class UpdateShippingStatusRequest(
    val status: String,
    val deliveredAt: LocalDateTime?
)
