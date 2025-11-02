package com.hhplus.ecommerce.infrastructure.order

import com.hhplus.ecommerce.model.order.Order
import com.hhplus.ecommerce.model.order.OrderStatus

/**
 * 주문 데이터 접근 인터페이스
 */
interface OrderRepository {

    /**
     * 주문 ID로 조회
     */
    fun findById(orderId: Long): Order?

    /**
     * 사용자의 주문 목록 조회
     */
    fun findByUserId(userId: Long): List<Order>

    /**
     * 사용자의 주문 목록 조회 (상태 필터링)
     */
    fun findByUserIdAndStatus(userId: Long, status: OrderStatus): List<Order>

    /**
     * 주문 저장 (추가 또는 수정)
     */
    fun save(order: Order): Order

    /**
     * 주문 ID 생성
     */
    fun generateId(): Long

    /**
     * 주문 아이템 ID 생성
     */
    fun generateItemId(): Long

    /**
     * 주문 번호 생성 (ORD-YYYYMMDD-ID)
     */
    fun generateOrderNumber(orderId: Long): String
}
