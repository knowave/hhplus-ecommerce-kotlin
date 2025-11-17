package com.hhplus.ecommerce.domain.product.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import jakarta.persistence.*

@Entity
@Table(
    name = "product",
    indexes = [
        // 카테고리별 상태 조회
        Index(name = "idx_product_category", columnList = "category"),

        // 카테고리별 인기 상품
        Index(name = "idx_product_category_sales", columnList = "category, sales_count DESC"),

        // 카테고리별 가격 범위 검색
        Index(name = "idx_product_category_price", columnList = "category, price"),

        // 재고 있는 상품 필터링
        Index(name = "idx_product_stock", columnList = "stock"),

        // 전체 인기 상품 조회 (카테고리 필터 없이 sales_count로만 정렬)
        Index(name = "idx_product_sales_count", columnList = "sales_count DESC")
    ]
)
class Product(
    @Column(nullable = false, length = 255)
    val name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val description: String,

    @Column(nullable = false)
    val price: Long,

    @Column(nullable = false)
    var stock: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val category: ProductCategory,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_specifications", joinColumns = [JoinColumn(name = "product_id")])
    @MapKeyColumn(name = "spec_key", length = 100)
    @Column(name = "spec_value", length = 500)
    val specifications: Map<String, String> = emptyMap(),

    @Column(nullable = false)
    var salesCount: Int = 0
) : BaseEntity() {
    /**
     * 재고 차감
     * @param quantity 차감할 수량
     * @throws InsufficientStockException 재고가 부족한 경우
     */
    fun deductStock(quantity: Int) {
        if (stock < quantity) {
            throw InsufficientStockException(
                productId = id!!,
                requested = quantity,
                available = stock
            )
        }
        stock -= quantity
    }

    /**
     * 재고 복구
     * @param quantity 복구할 수량
     */
    fun restoreStock(quantity: Int) {
        stock += quantity
    }

    /**
     * 판매 수량 증가
     * @param quantity 판매된 수량
     */
    fun increaseSalesCount(quantity: Int) {
        salesCount += quantity
    }
}