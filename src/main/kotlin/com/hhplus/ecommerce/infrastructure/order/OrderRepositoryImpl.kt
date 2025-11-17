package com.hhplus.ecommerce.infrastructure.order

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.repository.OrderRepository
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 주문 인메모리 Repository 구현체
 *
 * 이유: 실제 DB 없이 메모리에서 주문 데이터를 관리합니다.
 * Cart, User Repository와 동일한 패턴으로 구현하여 일관성을 유지합니다.
 */
@Repository
class OrderRepositoryImpl : OrderRepository {
    // Mock 데이터 저장소
    private val orders: MutableMap<UUID, Order> = mutableMapOf()

    private fun assignId(order: Order) {
        if (order.id == null) {
            val idField = order.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(order, java.util.UUID.randomUUID())
        }
    }

    override fun findById(orderId: UUID): Order? {
        return orders[orderId]
    }

    override fun findByUserId(userId: UUID): List<Order> {
        return orders.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }

    override fun findByUserIdAndStatus(userId: UUID, status: OrderStatus): List<Order> {
        return orders.values
            .filter { it.userId == userId && it.status == status }
            .sortedByDescending { it.createdAt }
    }

    override fun save(order: Order): Order {
        assignId(order)
        orders[order.id!!] = order
        return order
    }

    override fun generateOrderNumber(orderId: UUID): String {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "ORD-$dateStr-$orderId"
    }
}
