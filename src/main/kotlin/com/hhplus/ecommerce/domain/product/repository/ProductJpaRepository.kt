package com.hhplus.ecommerce.domain.product.repository

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProductJpaRepository : JpaRepository<Product, UUID> {
    @Query("""
        SELECT p FROM Product p
        WHERE (:category IS NULL OR p.category = :category)
    """)
    fun findAllWithFilter(
        @Param("category") category: ProductCategory?,
        pageable: Pageable
    ): Page<Product>
}