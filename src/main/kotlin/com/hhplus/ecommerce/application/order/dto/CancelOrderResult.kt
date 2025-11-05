package com.hhplus.ecommerce.application.order.dto

data class CancelOrderResult(
    val orderId: Long,
    val status: String,
    val cancelledAt: String,
    val refund: RefundInfoDto
)

data class RefundInfoDto(
    val restoredStock: List<RestoredStockItemDto>,
    val restoredCoupon: RestoredCouponInfoDto?
)

data class RestoredStockItemDto(
    val productId: Long,
    val quantity: Int
)

data class RestoredCouponInfoDto(
    val couponId: Long,
    val status: String
)
