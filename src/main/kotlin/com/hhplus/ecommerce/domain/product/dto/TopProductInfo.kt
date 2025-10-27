package com.hhplus.ecommerce.domain.product.dto

import java.math.BigDecimal

/**
 * 인기 상품 조회 쿼리 결과를 담는 DTO
 */
data class TopProductInfo(
    val productId: String,
    val productName: String,
    val salesCount: Long,
    val revenue: BigDecimal
)