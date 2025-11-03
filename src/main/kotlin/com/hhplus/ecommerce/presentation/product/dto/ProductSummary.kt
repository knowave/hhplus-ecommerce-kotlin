package com.hhplus.ecommerce.presentation.product.dto

data class ProductSummary(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val stock: Int,
    val salesCount: Int,
    val category: String,
    val createdAt: String,
    val updatedAt: String
)