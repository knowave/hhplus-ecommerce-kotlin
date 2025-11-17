package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.application.shipping.dto.*
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import java.time.LocalDateTime
import java.util.UUID

/**
 * 배송 비즈니스 로직 인터페이스
 */
interface ShippingService {

    /**
     * 배송 조회 (단건)
     */
    fun getShipping(orderId: UUID): ShippingResult

    /**
     * 배송 상태 변경
     */
    fun updateShippingStatus(
        shippingId: UUID,
        request: UpdateShippingStatusCommand
    ): UpdateShippingStatusResult

    /**
     * 사용자의 배송 목록 조회
     */
    fun getUserShippings(
        userId: UUID,
        status: String?,
        carrier: String?,
        from: String?,
        to: String?,
        page: Int,
        size: Int
    ): UserShippingListResult

    /**
     * 배송 생성
     */
    fun createShipping(orderId: UUID, carrier: String): Shipping
}