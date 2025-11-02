package com.hhplus.ecommerce.presentation.product.dto

data class ProductListResponse(
    val products: List<ProductSummary>,
    val pagination: Pagination
)