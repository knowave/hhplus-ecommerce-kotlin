package com.hhplus.ecommerce.presentation.shipping.dto

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import java.time.LocalDateTime

/**
 * 배송 조회 응답
 */
data class ShippingDetailResponse(
    val shippingId: Long,
    val orderId: Long,
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
        fun from(result: Shipping): ShippingDetailResponse {
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
