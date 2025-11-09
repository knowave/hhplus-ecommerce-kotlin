package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.domain.product.entity.Product
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProductJpaRepository : JpaRepository<Product, UUID> {
}