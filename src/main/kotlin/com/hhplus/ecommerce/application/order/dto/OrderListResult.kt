package com.hhplus.ecommerce.application.order.dto

data class OrderListResult(
    val orders: List<OrderSummaryDto>,
    val pagination: PaginationInfoDto
)

data class OrderSummaryDto(
    val orderId: Long,
    val orderNumber: String,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val status: String,
    val itemCount: Int,
    val createdAt: String
)

data class PaginationInfoDto(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)