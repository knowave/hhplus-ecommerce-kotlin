package com.hhplus.ecommerce.application.product.dto

import java.util.UUID

data class PeriodResult(
    val days: Int,
    val startDate: String,
    val endDate: String
)

data class TopProductItemResult(
    val rank: Int,
    val id: UUID,
    val name: String,
    val price: Long,
    val category: String,
    val salesCount: Int,
    val revenue: Long,
    val stock: Int
)

data class TopProductsResult(
    val period: PeriodResult,
    val products: List<TopProductItemResult>
)