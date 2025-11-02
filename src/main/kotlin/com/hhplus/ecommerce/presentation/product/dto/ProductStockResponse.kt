package com.hhplus.ecommerce.presentation.product.dto

data class ProductStockResponse(
    val id: Long,
    val productName: String,
    val stock: Int,
    val isAvailable: Boolean,
    val lastUpdatedAt: String
)