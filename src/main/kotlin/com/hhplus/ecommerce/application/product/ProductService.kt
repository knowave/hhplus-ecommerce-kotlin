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

    /**
     * 비관적 락을 사용하여 상품을 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용:
     * - 재고 차감/복원 시
     */
    fun findByIdWithLock(id: UUID): Product

    /**
     * 비관적 락을 사용하여 여러 상품을 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용:
     * - 주문 시 여러 상품의 재고를 동시에 차감
     * - 데드락 방지를 위해 ID 정렬됨
     */
    fun findAllByIdWithLock(ids: List<UUID>): List<Product>
}