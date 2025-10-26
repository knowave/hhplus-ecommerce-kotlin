package com.hhplus.ecommerce.domain.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, String> {

    fun findByUserId(userId: String): List<Order>

    fun findByUserIdAndStatus(userId: String, status: OrderStatus): List<Order>
}
