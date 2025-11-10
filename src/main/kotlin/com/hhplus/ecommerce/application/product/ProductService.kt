package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.*
import com.hhplus.ecommerce.domain.product.entity.Product
import java.util.UUID

interface ProductService {
    /**
     * 상품 목록을 조회합니다.
     * 카테고리 필터링, 정렬, 페이징을 지원합니다.
     */
    fun getProducts(request: GetProductsCommand): ProductListResult

    fun getTopProducts(days: Int, limit: Int): TopProductsResult

    fun findProductById(id: UUID): Product

    fun updateProduct(product: Product): Product
}