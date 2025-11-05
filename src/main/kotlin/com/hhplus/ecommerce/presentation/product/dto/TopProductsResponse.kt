package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.application.product.dto.TopProductsResult

data class TopProductsResponse(
    val period: Period,
    val products: List<TopProductItem>
) {
    companion object {
        fun from(result: TopProductsResult): TopProductsResponse {
            return TopProductsResponse(
                period = Period.from(result.period),
                products = result.products.map { TopProductItem.from(it) }
            )
        }
    }
}