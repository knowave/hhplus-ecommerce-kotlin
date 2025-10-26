package com.hhplus.ecommerce.domain.order

import com.hhplus.ecommerce.domain.order.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, String> {

    fun findByOrderId(orderId: String): List<OrderItem>

    fun findByProductId(productId: String): List<OrderItem>
}
