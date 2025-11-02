package com.hhplus.ecommerce.presentation.order.dto

/**
 * 주문 취소 응답 DTO
 */
data class CancelOrderResponse(
    val orderId: Long,
    val status: String,
    val cancelledAt: String,
    val refund: RefundInfo
)

data class RefundInfo(
    val restoredStock: List<RestoredStockItem>,
    val restoredCoupon: RestoredCouponInfo?
)

data class RestoredStockItem(
    val productId: Long,
    val quantity: Int
)

data class RestoredCouponInfo(
    val couponId: Long,
    val status: String
)