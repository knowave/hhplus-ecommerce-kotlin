package com.hhplus.ecommerce.infrastructure.order

import com.hhplus.ecommerce.model.order.Order
import com.hhplus.ecommerce.model.order.OrderStatus
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 주문 인메모리 Repository 구현체
 *
 * 이유: 실제 DB 없이 메모리에서 주문 데이터를 관리합니다.
 * Cart, User Repository와 동일한 패턴으로 구현하여 일관성을 유지합니다.
 */
@Repository
class OrderRepositoryImpl : OrderRepository {

    // ID 자동 생성을 위한 카운터
    private var nextOrderId: Long = 1001L
    private var nextItemId: Long = 1L

    // Mock 데이터 저장소: orderId -> Order
    private val orders: MutableMap<Long, Order> = mutableMapOf()

    override fun findById(orderId: Long): Order? {
        return orders[orderId]
    }

    override fun findByUserId(userId: Long): List<Order> {
        return orders.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }

    override fun findByUserIdAndStatus(userId: Long, status: OrderStatus): List<Order> {
        return orders.values
            .filter { it.userId == userId && it.status == status }
            .sortedByDescending { it.createdAt }
    }

    override fun save(order: Order): Order {
        orders[order.orderId] = order
        return order
    }

    override fun generateId(): Long {
        return nextOrderId++
    }

    override fun generateItemId(): Long {
        return nextItemId++
    }

    override fun generateOrderNumber(orderId: Long): String {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "ORD-$dateStr-$orderId"
    }
}
