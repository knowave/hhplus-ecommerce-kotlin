package com.hhplus.ecommerce.domains.product.dto

data class ProductSummary(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val category: String,
    val createdAt: String,
    val updatedAt: String
)