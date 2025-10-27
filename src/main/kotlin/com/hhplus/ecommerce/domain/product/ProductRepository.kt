package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.domain.product.dto.TopProductInfo
import com.hhplus.ecommerce.domain.product.entity.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.*

@Repository
interface ProductRepository : JpaRepository<Product, String> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithOptimisticLock(id: String): Optional<Product>

    fun findByCategory(category: String): List<Product>

    @Query("""
        SELECT new com.hhplus.ecommerce.domain.product.dto.TopProductInfo(
            oi.productId,
            p.name,
            SUM(oi.quantity),
            SUM(oi.subtotal)
        )
        FROM OrderItem oi
        JOIN Product p ON oi.productId = p.id
        JOIN Order o ON oi.order.id = o.id
        WHERE o.createdAt >= :fromDate
        AND o.status = 'PAID'
        GROUP BY oi.productId, p.name
        ORDER BY SUM(oi.quantity) DESC
    """)
    fun findTopProductsByRecentSales(fromDate: LocalDateTime, limit: Int): List<TopProductInfo>
}
