package com.hhplus.ecommerce.domains.order.dto

/**
 * 주문 상세 조회 응답 DTO
 */
data class OrderDetailResponse(
    val orderId: Long,
    val userId: Long,
    val orderNumber: String,
    val items: List<OrderItemResponse>,
    val pricing: PricingInfo,
    val status: String,
    val payment: PaymentInfo?,
    val createdAt: String,
    val updatedAt: String
)

data class PaymentInfo(
    val paidAmount: Long,
    val paidAt: String
)