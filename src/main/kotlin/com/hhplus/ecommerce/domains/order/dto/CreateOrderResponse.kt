package com.hhplus.ecommerce.domains.order.dto

/**
 * 주문 생성 응답 DTO
 */
data class CreateOrderResponse(
    val orderId: Long,
    val userId: Long,
    val orderNumber: String,
    val items: List<OrderItemResponse>,
    val pricing: PricingInfo,
    val status: String,
    val createdAt: String
)

data class OrderItemResponse(
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long
)

data class PricingInfo(
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val appliedCoupon: AppliedCouponInfo?
)

data class AppliedCouponInfo(
    val couponId: Long,
    val couponName: String,
    val discountRate: Int
)