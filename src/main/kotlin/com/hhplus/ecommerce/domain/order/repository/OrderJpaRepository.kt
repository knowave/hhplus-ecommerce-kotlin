package com.hhplus.ecommerce.domain.order.repository

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderJpaRepository : JpaRepository<Order, UUID> {
    fun findByUserIdAndStatus(userId: UUID, stats: OrderStatus): List<Order>

    fun findByUserId(userId: UUID): List<Order>
}