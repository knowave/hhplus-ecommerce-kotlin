package com.hhplus.ecommerce.domains.product.dto

data class ProductListResponse(
    val products: List<ProductSummary>,
    val pagination: Pagination
)