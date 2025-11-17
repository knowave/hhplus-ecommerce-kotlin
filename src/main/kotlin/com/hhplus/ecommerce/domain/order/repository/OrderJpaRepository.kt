package com.hhplus.ecommerce.domain.order.repository

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderJpaRepository : JpaRepository<Order, UUID> {
    fun findByUserId(userId: UUID): List<Order>

    @Query("""
        SELECT o FROM Order o
        WHERE o.userId = :userId
        AND (:status IS NULL OR o.status = :status)
        ORDER BY o.createdAt DESC
    """)
    fun findByUserIdWithPaging(
        @Param("userId") userId: UUID,
        @Param("status") status: OrderStatus?,
        pageable: Pageable
    ): Page<Order>
}