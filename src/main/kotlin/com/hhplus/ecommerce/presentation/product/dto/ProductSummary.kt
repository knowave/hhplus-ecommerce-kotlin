package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.domain.product.entity.Product
import java.time.LocalDateTime
import java.util.UUID

data class ProductSummary(
    val id: UUID,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val salesCount: Int,
    val category: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(product: Product): ProductSummary {
            return ProductSummary(
                id = product.id!!,
                name = product.name,
                description = product.description,
                price = product.price,
                stock = product.stock,
                salesCount = product.salesCount,
                category = product.category.toString(),
                createdAt = product.createdAt!!,
                updatedAt = product.updatedAt!!
            )
        }
    }
}