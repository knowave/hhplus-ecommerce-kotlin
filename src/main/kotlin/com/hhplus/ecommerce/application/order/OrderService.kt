package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.order.dto.*
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest

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
    fun getOrderDetail(orderId: Long, userId: Long): OrderDetailResult

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrders(userId: Long, status: String?, page: Int, size: Int): OrderListResult

    /**
     * 주문 취소
     */
    fun cancelOrder(orderId: Long, request: CancelOrderCommand): CancelOrderResult

    fun getOrder(id: Long): Order

    fun updateOrder(order: Order): Order
}