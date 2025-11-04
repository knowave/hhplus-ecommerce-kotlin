package com.hhplus.ecommerce.domain.product.entity

import com.hhplus.ecommerce.common.exception.InsufficientStockException

data class Product(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    var stock: Int,
    val category: ProductCategory,
    val specifications: Map<String, String> = emptyMap(),
    var salesCount: Int = 0,
    val createdAt: String,
    var updatedAt: String
) {
    /**
     * 재고 차감
     * @param quantity 차감할 수량
     * @throws InsufficientStockException 재고가 부족한 경우
     */
    fun deductStock(quantity: Int) {
        if (stock < quantity) {
            throw InsufficientStockException(
                productId = id,
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