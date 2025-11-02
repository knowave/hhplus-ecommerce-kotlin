package com.hhplus.ecommerce.presentation.product.dto

data class TopProductsResponse(
    val period: Period,
    val products: List<TopProductItem>
)