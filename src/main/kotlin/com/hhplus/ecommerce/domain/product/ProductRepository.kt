package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.domain.product.entity.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import java.util.*

@Repository
interface ProductRepository : JpaRepository<Product, String> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithOptimisticLock(id: String): Optional<Product>

    fun findByCategory(category: String): List<Product>
}
