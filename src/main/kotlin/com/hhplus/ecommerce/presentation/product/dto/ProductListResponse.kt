package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.application.product.dto.ProductListResult

data class ProductListResponse(
    val products: List<ProductSummary>,
    val pagination: Pagination
) {
    companion object {
        fun from(result: ProductListResult): ProductListResponse {
            return ProductListResponse(
                products = result.products.map { ProductSummary.from(it) },
                pagination = Pagination(
                    currentPage = result.pagination.currentPage,
                    totalPages = result.pagination.totalPages,
                    totalElements = result.pagination.totalElements,
                    size = result.pagination.size,
                    hasNext = result.pagination.hasNext,
                    hasPrevious = result.pagination.hasPrevious
                )
            )
        }
    }
}