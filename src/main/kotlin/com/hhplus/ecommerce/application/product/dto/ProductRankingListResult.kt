package com.hhplus.ecommerce.application.product.dto

import java.util.UUID

data class ProductRankingListResult(
    val period: String,
    val date: String,
    val rankings: List<ProductRanking>,
    val totalCount: Int
)

data class ProductRanking(
    val rank: Int,
    val productId: UUID,
    val productName: String,
    val orderCount: Long,
    val category: String,
    val price: Long,
    val salesCount: Int
)