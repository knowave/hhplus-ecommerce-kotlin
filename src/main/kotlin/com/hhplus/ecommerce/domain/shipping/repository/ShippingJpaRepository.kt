package com.hhplus.ecommerce.domain.shipping.repository

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface ShippingJpaRepository : JpaRepository<Shipping, UUID> {
    
    @Query("SELECT s FROM Shipping s WHERE s.orderId = :orderId")
    fun findByOrderId(@Param("orderId") orderId: UUID): Shipping?
    
    @Query("""
        SELECT s FROM Shipping s
        JOIN Order o ON s.orderId = o.id
        WHERE o.userId = :userId
        AND (:status IS NULL OR s.status = :status)
        AND (:carrier IS NULL OR s.carrier = :carrier)
        AND (:from IS NULL OR s.createdAt >= :from)
        AND (:to IS NULL OR s.createdAt <= :to)
        ORDER BY s.createdAt DESC
    """)
    fun findByUserIdWithFilters(
        @Param("userId") userId: UUID,
        @Param("status") status: ShippingStatus?,
        @Param("carrier") carrier: String?,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?,
        pageable: Pageable
    ): Page<Shipping>

    @Query("""
        SELECT s FROM Shipping s
        JOIN Order o ON s.orderId = o.id
        WHERE o.userId = :userId
        AND (:status IS NULL OR s.status = :status)
        AND (:carrier IS NULL OR s.carrier = :carrier)
        AND (:from IS NULL OR s.createdAt >= :from)
        AND (:to IS NULL OR s.createdAt <= :to)
    """)
    fun findAllByUserIdWithFilters(
        @Param("userId") userId: UUID,
        @Param("status") status: ShippingStatus?,
        @Param("carrier") carrier: String?,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?
    ): List<Shipping>
}