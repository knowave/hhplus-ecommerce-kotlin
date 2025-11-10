package com.hhplus.ecommerce.presentation.shipping.dto

import com.hhplus.ecommerce.application.shipping.dto.ShippingResult
import java.time.LocalDateTime
import java.util.UUID

/**
 * 배송 조회 응답
 */
data class ShippingDetailResponse(
    val shippingId: UUID,
    val orderId: UUID,
    val carrier: String,
    val trackingNumber: String,
    val shippingStartAt: LocalDateTime?,
    val estimatedArrivalAt: LocalDateTime,
    val deliveredAt: LocalDateTime?,
    val status: String,
    val isDelayed: Boolean,
    val isExpired: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(result: ShippingResult): ShippingDetailResponse {
            return ShippingDetailResponse(
                shippingId = result.id,
                orderId = result.orderId,
                carrier = result.carrier,
                trackingNumber = result.trackingNumber,
                shippingStartAt = result.shippingStartAt,
                estimatedArrivalAt = result.estimatedArrivalAt,
                deliveredAt = result.deliveredAt,
                status = result.status.name,
                isDelayed = result.isDelayed,
                isExpired = result.isExpired,
                createdAt = result.createdAt,
                updatedAt = result.updatedAt
            )
        }
    }
}
