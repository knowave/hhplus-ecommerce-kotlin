package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.application.product.dto.TopProductItemResult
import java.util.UUID

data class TopProductItem(
    val rank: Int,
    val id: UUID,
    val name: String,
    val price: Long,
    val category: String,
    val salesCount: Int,
    val revenue: Long,
    val stock: Int
) {
    companion object {
        fun from(result: TopProductItemResult): TopProductItem {
            return TopProductItem(
                rank = result.rank,
                id = result.id,
                name = result.name,
                price = result.price,
                category = result.category,
                salesCount = result.salesCount,
                revenue = result.revenue,
                stock = result.stock
            )
        }
    }
}