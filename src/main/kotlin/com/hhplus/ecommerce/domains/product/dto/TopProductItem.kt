package com.hhplus.ecommerce.domains.product.dto

data class TopProductItem(
    val rank: Int,
    val id: Long,
    val name: String,
    val price: Long,
    val category: String,
    val salesCount: Int,
    val revenue: Long,
    val stock: Int
)