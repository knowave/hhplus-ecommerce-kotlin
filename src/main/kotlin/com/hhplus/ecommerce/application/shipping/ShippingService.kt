package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.presentation.shipping.dto.*
/**
 * 배송 비즈니스 로직 인터페이스
 */
interface ShippingService {

    /**
     * 배송 조회 (단건)
     */
    fun getShipping(orderId: Long): ShippingDetailResponse

    /**
     * 배송 상태 변경
     */
    fun updateShippingStatus(
        shippingId: Long,
        request: UpdateShippingStatusRequest
    ): UpdateShippingStatusResponse

    /**
     * 사용자의 배송 목록 조회
     */
    fun getUserShippings(
        userId: Long,
        status: String?,
        carrier: String?,
        from: String?,
        to: String?,
        page: Int,
        size: Int
    ): UserShippingListResponse
}