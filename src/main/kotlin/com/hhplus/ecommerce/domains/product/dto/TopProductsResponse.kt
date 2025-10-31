package com.hhplus.ecommerce.domains.product.dto

data class TopProductsResponse(
    val period: Period,
    val products: List<TopProductItem>
)