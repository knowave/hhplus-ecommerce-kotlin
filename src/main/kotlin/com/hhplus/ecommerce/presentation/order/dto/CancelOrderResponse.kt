package com.hhplus.ecommerce.presentation.order.dto

import com.hhplus.ecommerce.application.order.dto.*
import java.util.UUID

/**
 * 주문 취소 응답 DTO
 */
data class CancelOrderResponse(
    val orderId: UUID,
    val status: String,
    val cancelledAt: String,
    val refund: RefundInfo
) {
    companion object {
        fun from(result: CancelOrderResult): CancelOrderResponse {
            return CancelOrderResponse(
                orderId = result.orderId,
                status = result.status,
                cancelledAt = result.cancelledAt,
                refund = RefundInfo.from(result.refund)
            )
        }
    }
}

data class RefundInfo(
    val restoredStock: List<RestoredStockItem>,
    val restoredCoupon: RestoredCouponInfo?
) {
    companion object {
        fun from(result: RefundInfoDto): RefundInfo {
            return RefundInfo(
                restoredStock = result.restoredStock.map { RestoredStockItem.from(it) },
                restoredCoupon = result.restoredCoupon?.let { RestoredCouponInfo.from(it) }
            )
        }
    }
}

data class RestoredStockItem(
    val productId: UUID,
    val quantity: Int
) {
    companion object {
        fun from(result: RestoredStockItemDto): RestoredStockItem {
            return RestoredStockItem(
                productId = result.productId,
                quantity = result.quantity
            )
        }
    }
}

data class RestoredCouponInfo(
    val couponId: UUID,
    val status: String
) {
    companion object {
        fun from(result: RestoredCouponInfoDto): RestoredCouponInfo {
            return RestoredCouponInfo(
                couponId = result.couponId,
                status = result.status
            )
        }
    }
}