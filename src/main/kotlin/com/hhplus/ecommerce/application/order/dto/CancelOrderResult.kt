package com.hhplus.ecommerce.application.order.dto

import java.util.UUID

data class CancelOrderResult(
    val orderId: UUID,
    val status: String,
    val cancelledAt: String,
    val refund: RefundInfoDto
)

data class RefundInfoDto(
    val restoredStock: List<RestoredStockItemDto>,
    val restoredCoupon: RestoredCouponInfoDto?
)

data class RestoredStockItemDto(
    val productId: UUID,
    val quantity: Int
)

data class RestoredCouponInfoDto(
    val couponId: UUID,
    val status: String
)
