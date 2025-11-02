package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderResponse
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderResponse
import com.hhplus.ecommerce.presentation.order.dto.OrderDetailResponse
import com.hhplus.ecommerce.presentation.order.dto.OrderListResponse

/**
 * 주문 비즈니스 로직 인터페이스
 */
interface OrderService {

    /**
     * 주문 생성
     */
    fun createOrder(request: CreateOrderRequest): CreateOrderResponse

    /**
     * 주문 상세 조회
     */
    fun getOrderDetail(orderId: Long, userId: Long): OrderDetailResponse

    /**
     * 사용자 주문 목록 조회
     */
    fun getOrders(userId: Long, status: String?, page: Int, size: Int): OrderListResponse

    /**
     * 주문 취소
     */
    fun cancelOrder(orderId: Long, request: CancelOrderRequest): CancelOrderResponse
}