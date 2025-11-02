package com.hhplus.ecommerce.presentation.order.dto

/**
 * 주문 목록 조회 응답 DTO
 */
data class OrderListResponse(
    val orders: List<OrderSummary>,
    val pagination: PaginationInfo
)

data class OrderSummary(
    val orderId: Long,
    val orderNumber: String,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val status: String,
    val itemCount: Int,
    val createdAt: String
)

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)