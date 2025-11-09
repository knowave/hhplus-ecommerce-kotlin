package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import java.util.UUID

/**
 * 상품 데이터 접근을 담당하는 Repository 인터페이스
 */
interface ProductRepository {

    /**
     * 상품 ID로 상품을 조회합니다.
     */
    fun findById(productId: UUID): Product?

    /**
     * 모든 상품을 조회합니다.
     */
    fun findAll(): List<Product>

    /**
     * 카테고리로 상품을 필터링합니다.
     */
    fun findByCategory(category: ProductCategory): List<Product>

    /**
     * 상품 정보를 저장하거나 업데이트합니다.
     */
    fun save(product: Product): Product
}