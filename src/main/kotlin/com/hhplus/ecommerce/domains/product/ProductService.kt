package com.hhplus.ecommerce.domains.product

import com.hhplus.ecommerce.domains.product.dto.*

interface ProductService {

    fun getProducts(
        category: String?,
        sort: String?,
        page: Int,
        size: Int
    ): ProductListResponse

    fun getProductDetail(productId: Long): ProductDetailResponse

    fun getProductStock(productId: Long): ProductStockResponse

    fun getTopProducts(days: Int, limit: Int): TopProductsResponse
}