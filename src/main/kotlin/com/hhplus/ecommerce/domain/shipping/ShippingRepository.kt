package com.hhplus.ecommerce.domain.shipping

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import java.time.LocalDateTime

/**
 * 배송 데이터 접근 인터페이스
 */
interface ShippingRepository {

    /**
     * 배송 ID로 조회
     */
    fun findById(shippingId: Long): Shipping?

    /**
     * 주문 ID로 배송 조회
     */
    fun findByOrderId(orderId: Long): Shipping?

    /**
     * 사용자의 배송 목록 조회
     */
    fun findByUserId(userId: Long): List<Shipping>

    /**
     * 사용자의 배송 목록 조회 (필터링)
     */
    fun findByUserIdWithFilters(
        userId: Long,
        status: ShippingStatus?,
        carrier: String?,
        from: LocalDateTime?,
        to: LocalDateTime?
    ): List<Shipping>

    /**
     * 송장번호로 조회 (중복 체크용)
     */
    fun existsByTrackingNumber(trackingNumber: String): Boolean

    /**
     * 배송 저장 (추가 또는 수정)
     */
    fun save(shipping: Shipping): Shipping

    /**
     * 배송 ID 생성
     */
    fun generateId(): Long

    /**
     * 송장번호 생성
     */
    fun generateTrackingNumber(): String
}
