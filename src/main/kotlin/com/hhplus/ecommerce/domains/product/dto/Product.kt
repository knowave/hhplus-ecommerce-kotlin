package com.hhplus.ecommerce.domains.product.dto

import com.hhplus.ecommerce.domains.product.ProductCategory

data class Product(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    var stock: Int,
    val category: ProductCategory,
    val specifications: Map<String, String> = emptyMap(),
    var salesCount: Int = 0,
    val createdAt: String,
    var updatedAt: String
)