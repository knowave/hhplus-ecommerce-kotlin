package com.hhplus.ecommerce.domains.product.dto

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
)