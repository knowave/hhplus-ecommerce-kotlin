package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.domain.product.entity.Product

data class ProductStockResponse(
    val id: Long,
    val productName: String,
    val stock: Int,
    val isAvailable: Boolean,
    val lastUpdatedAt: String
) {
    companion object {
        fun from(product: Product): ProductStockResponse {
            return ProductStockResponse(
                id = product.id,
                productName = product.name,
                stock = product.stock,
                isAvailable = product.stock > 0,
                lastUpdatedAt = product.updatedAt
            )
        }
    }
}