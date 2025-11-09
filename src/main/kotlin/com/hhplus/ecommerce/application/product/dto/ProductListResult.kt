package com.hhplus.ecommerce.application.product.dto

import com.hhplus.ecommerce.domain.product.entity.Product

data class ProductListResult(
    val products: List<Product>,
    val pagination: PaginationResult
)
