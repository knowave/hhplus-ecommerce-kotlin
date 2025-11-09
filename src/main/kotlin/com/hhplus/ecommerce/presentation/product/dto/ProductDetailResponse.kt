package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.domain.product.entity.Product

data class ProductDetailResponse(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val category: String,
    val specifications: Map<String, String>,
    val salesCount: Int,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun  from(product: Product): ProductDetailResponse {
            return ProductDetailResponse(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                stock = product.stock,
                category = product.category.toString(),
                specifications = product.specifications,
                salesCount = product.salesCount,
                createdAt =  product.createdAt,
                updatedAt = product.updatedAt
            )
        }
    }
}