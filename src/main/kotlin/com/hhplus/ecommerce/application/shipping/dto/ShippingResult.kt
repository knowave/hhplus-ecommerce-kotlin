package com.hhplus.ecommerce.application.shipping.dto

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import java.time.LocalDateTime
import java.util.UUID

/**
 * 배송 조회 결과 DTO
 *
 * Shipping entity를 Service 레이어에서 반환할 때 사용
 * - JPA entity를 직접 노출하지 않고 DTO로 변환하여 반환
 * - BaseEntity의 nullable 필드(id, createdAt, updatedAt)를 non-nullable로 변환
 */
data class ShippingResult(
    val id: UUID,
    val orderId: UUID,
    val carrier: String,
    val trackingNumber: String,
    val shippingStartAt: LocalDateTime?,
    val estimatedArrivalAt: LocalDateTime,
    val deliveredAt: LocalDateTime?,
    val status: ShippingStatus,
    val isDelayed: Boolean,
    val isExpired: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        /**
         * Shipping entity를 ShippingResult DTO로 변환
         *
         * @param shipping Shipping entity (DB에서 조회된 영속 상태)
         * @return ShippingResult DTO
         * @throws IllegalStateException id, createdAt, updatedAt이 null인 경우
         */
        fun from(shipping: Shipping): ShippingResult {
            requireNotNull(shipping.id) { "Shipping id must not be null" }
            requireNotNull(shipping.createdAt) { "Shipping createdAt must not be null" }
            requireNotNull(shipping.updatedAt) { "Shipping updatedAt must not be null" }

            return ShippingResult(
                id = shipping.id!!,
                orderId = shipping.orderId,
                carrier = shipping.carrier,
                trackingNumber = shipping.trackingNumber,
                shippingStartAt = shipping.shippingStartAt,
                estimatedArrivalAt = shipping.estimatedArrivalAt,
                deliveredAt = shipping.deliveredAt,
                status = shipping.status,
                isDelayed = shipping.isDelayed,
                isExpired = shipping.isExpired,
                createdAt = shipping.createdAt!!,
                updatedAt = shipping.updatedAt!!
            )
        }
    }
}