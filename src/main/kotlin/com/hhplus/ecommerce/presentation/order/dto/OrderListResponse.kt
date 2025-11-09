package com.hhplus.ecommerce.presentation.order.dto

import com.hhplus.ecommerce.application.order.dto.OrderListResult
import com.hhplus.ecommerce.application.order.dto.OrderSummaryDto
import com.hhplus.ecommerce.application.order.dto.PaginationInfoDto

/**
 * 주문 목록 조회 응답 DTO
 */
data class OrderListResponse(
    val orders: List<OrderSummary>,
    val pagination: PaginationInfo
) {
    companion object {
        fun from(result: OrderListResult): OrderListResponse {
            return OrderListResponse(
                orders = result.orders.map { OrderSummary.from(it) },
                pagination = PaginationInfo.from(result.pagination)
            )
        }
    }
}

data class OrderSummary(
    val orderId: Long,
    val orderNumber: String,
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val status: String,
    val itemCount: Int,
    val createdAt: String
) {
    companion object {
        fun from(result: OrderSummaryDto): OrderSummary {
            return OrderSummary(
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                totalAmount = result.totalAmount,
                discountAmount = result.discountAmount,
                finalAmount = result.finalAmount,
                status = result.status,
                itemCount = result.itemCount,
                createdAt = result.createdAt
            )
        }
    }
}

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
    val size: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    companion object {
        fun from(result: PaginationInfoDto): PaginationInfo {
            return PaginationInfo(
                currentPage = result.currentPage,
                totalPages = result.totalPages,
                totalElements = result.totalElements,
                size = result.size,
                hasNext = result.hasNext,
                hasPrevious = result.hasPrevious
            )
        }
    }
}