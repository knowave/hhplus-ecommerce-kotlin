package com.hhplus.ecommerce.domain.order

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import java.util.UUID

/**
 * 주문 데이터 접근 인터페이스
 */
interface OrderRepository {
    fun findById(orderId: UUID): Order?

    fun findByUserId(userId: UUID): List<Order>

    fun findByUserIdAndStatus(userId: UUID, status: OrderStatus): List<Order>

    fun save(order: Order): Order

    fun generateOrderNumber(orderId: UUID): String
}