package com.hhplus.ecommerce.domains.product.dto

data class Pagination(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)