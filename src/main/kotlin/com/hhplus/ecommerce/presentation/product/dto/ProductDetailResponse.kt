package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.domain.product.entity.Product
import java.time.LocalDateTime
import java.util.UUID

data class ProductDetailResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val category: String,
    val specifications: Map<String, String>,
    val salesCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun  from(product: Product): ProductDetailResponse {
            return ProductDetailResponse(
                id = product.id!!,
                name = product.name,
                description = product.description,
                price = product.price,
                stock = product.stock,
                category = product.category.toString(),
                specifications = product.specifications,
                salesCount = product.salesCount,
                createdAt =  product.createdAt!!,
                updatedAt = product.updatedAt!!
            )
        }
    }
}