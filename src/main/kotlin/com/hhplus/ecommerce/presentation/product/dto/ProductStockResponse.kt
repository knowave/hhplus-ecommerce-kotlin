package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.domain.product.entity.Product
import java.time.LocalDateTime
import java.util.UUID

data class ProductStockResponse(
    val id: UUID,
    val productName: String,
    val stock: Int,
    val isAvailable: Boolean,
    val lastUpdatedAt: LocalDateTime
) {
    companion object {
        fun from(product: Product): ProductStockResponse {
            return ProductStockResponse(
                id = product.id!!,
                productName = product.name,
                stock = product.stock,
                isAvailable = product.stock > 0,
                lastUpdatedAt = product.updatedAt!!
            )
        }
    }
}