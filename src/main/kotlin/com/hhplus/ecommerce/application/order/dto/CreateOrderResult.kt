package com.hhplus.ecommerce.application.order.dto

import java.util.UUID

data class CreateOrderResult(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val items: List<OrderItemResult>,
    val pricing: PricingInfoDto,
    val status: String,
    val createdAt: String
)

data class OrderItemResult(
    val orderItemId: UUID,
    val productId: UUID,
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
    val couponId: UUID,
    val couponName: String,
    val discountRate: Int
)