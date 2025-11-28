package com.hhplus.ecommerce.domain.order.repository

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface OrderJpaRepository : JpaRepository<Order, UUID> {
    fun findByUserId(userId: UUID): List<Order>

    /**
     * 주문 상세 조회 시 N+1 쿼리 방지를 위해 Fetch Join 사용
     */
    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.id = :orderId
    """)
    fun findByIdWithItems(@Param("orderId") orderId: UUID): Optional<Order>

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

    /**
     * 비관적 락을 사용하여 주문 조회
     * 동시성 제어가 필요한 결제 처리 등에서 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    fun findByIdWithLock(@Param("orderId") orderId: UUID): Optional<Order>
}