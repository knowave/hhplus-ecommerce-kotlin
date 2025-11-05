package com.hhplus.ecommerce.application.order.dto

data class CreateOrderResult(
    val orderId: Long,
    val userId: Long,
    val orderNumber: String,
    val items: List<OrderItemResult>,
    val pricing: PricingInfoDto,
    val status: String,
    val createdAt: String
)

data class OrderItemResult(
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long
)

data class PricingInfoDto(
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val appliedCoupon: AppliedCouponInfoDto?
)

data class AppliedCouponInfoDto(
    val couponId: Long,
    val couponName: String,
    val discountRate: Int
)