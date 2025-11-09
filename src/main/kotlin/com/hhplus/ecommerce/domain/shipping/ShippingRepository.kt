package com.hhplus.ecommerce.domain.shipping

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import java.time.LocalDateTime
import java.util.UUID

/**
 * 배송 데이터 접근 인터페이스
 */
interface ShippingRepository {
    fun findById(shippingId: UUID): Shipping?

    fun findByOrderId(orderId: UUID): Shipping?

    fun findByUserId(userId: UUID): List<Shipping>

    fun findByUserIdWithFilters(
        userId: UUID,
        status: ShippingStatus?,
        carrier: String?,
        from: LocalDateTime?,
        to: LocalDateTime?
    ): List<Shipping>

    fun existsByTrackingNumber(trackingNumber: String): Boolean

    fun save(shipping: Shipping): Shipping

    fun generateTrackingNumber(): String
}
