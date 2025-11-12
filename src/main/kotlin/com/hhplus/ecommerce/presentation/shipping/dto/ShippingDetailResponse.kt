package com.hhplus.ecommerce.presentation.shipping.dto

import com.hhplus.ecommerce.application.shipping.dto.ShippingResult
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

/**
 * 배송 조회 응답
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ShippingDetailResponse(
    val shippingId: UUID = UUID.randomUUID(),
    val orderId: UUID = UUID.randomUUID(),
    val carrier: String = "",
    val trackingNumber: String = "",
    val shippingStartAt: LocalDateTime? = null,
    val estimatedArrivalAt: LocalDateTime = LocalDateTime.now(),
    val deliveredAt: LocalDateTime? = null,
    val status: String = "",
    val isDelayed: Boolean = false,
    val isExpired: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
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
