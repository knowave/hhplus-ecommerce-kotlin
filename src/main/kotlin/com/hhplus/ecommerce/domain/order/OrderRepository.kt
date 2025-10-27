package com.hhplus.ecommerce.domain.order

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, String> {

    fun findByUserId(userId: String): List<Order>

    fun findByUserIdAndStatus(userId: String, status: OrderStatus): List<Order>
}
