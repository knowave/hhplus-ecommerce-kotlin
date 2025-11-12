package com.hhplus.ecommerce.presentation.shipping.dto

import com.hhplus.ecommerce.application.shipping.dto.UpdateShippingStatusResult
import java.time.LocalDateTime
import java.util.UUID

/**
 * 배송 상태 변경 응답
 */
data class UpdateShippingStatusResponse(
    val shippingId: UUID,
    val orderId: UUID,
    val status: String,
    val deliveredAt: LocalDateTime?,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(result: UpdateShippingStatusResult): UpdateShippingStatusResponse {
            return UpdateShippingStatusResponse(
                shippingId = result.shippingId,
                orderId = result.orderId,
                status = result.status,
                deliveredAt = result.deliveredAt,
                updatedAt = result.updatedAt
            )
        }
    }
}
