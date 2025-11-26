package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.order.dto.*
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import java.util.UUID

/**
 * 주문 비즈니스 로직 인터페이스
 */
interface OrderService {

    /**
     * 주문 생성
     */
    fun createOrder(request: CreateOrderCommand): CreateOrderResult

    /**
     * 주문 상세 조회
     */
    fun getOrderDetail(orderId: UUID, userId: UUID): OrderDetailResult

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrders(userId: UUID, status: String?, page: Int, size: Int): OrderListResult

    /**
     * 주문 취소
     */
    fun cancelOrder(orderId: UUID, request: CancelOrderCommand): CancelOrderResult

    fun getOrder(id: UUID): Order

    /**
     * 비관적 락을 사용하여 주문 조회
     * 동시성 제어가 필요한 결제 처리 등에서 사용
     */
    fun getOrderWithLock(id: UUID): Order

    fun updateOrder(order: Order): Order
}