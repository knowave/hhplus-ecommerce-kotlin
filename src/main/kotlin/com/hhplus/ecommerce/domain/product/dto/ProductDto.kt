package com.hhplus.ecommerce.domain.product.dto

import java.math.BigDecimal

data class ProductResponseDto(
    val productId: String,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val category: String
)

data class TopProductResponseDto(
    val rank: Int,
    val productId: String,
    val name: String,
    val salesCount: Long,
    val revenue: BigDecimal
)

data class TopProductsResponseDto(
    val period: String,
    val products: List<TopProductResponseDto>
)