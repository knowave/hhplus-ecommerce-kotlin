package com.hhplus.ecommerce.presentation.order.dto

import com.hhplus.ecommerce.application.order.dto.AppliedCouponInfoDto
import com.hhplus.ecommerce.application.order.dto.CreateOrderResult
import com.hhplus.ecommerce.application.order.dto.OrderItemResult
import com.hhplus.ecommerce.application.order.dto.PricingInfoDto
import java.util.UUID

/**
 * 주문 생성 응답 DTO
 */
data class CreateOrderResponse(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val items: List<OrderItemResponse>,
    val pricing: PricingInfo,
    val status: String,
    val createdAt: String
) {
    companion object {
        fun from(result: CreateOrderResult): CreateOrderResponse {
            return CreateOrderResponse(
                orderId = result.orderId,
                userId = result.userId,
                orderNumber = result.orderNumber,
                items = result.items.map { OrderItemResponse.from(it) },
                pricing = PricingInfo.from(result.pricing),
                status = result.status,
                createdAt = result.createdAt
            )
        }
    }
}

data class OrderItemResponse(
    val orderItemId: UUID,
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long
) {
    companion object {
        fun from(result: OrderItemResult): OrderItemResponse {
            return OrderItemResponse(
                orderItemId = result.orderItemId,
                productId = result.productId,
                productName = result.productName,
                quantity = result.quantity,
                unitPrice = result.unitPrice,
                subtotal = result.subtotal
            )
        }
    }
}

data class PricingInfo(
    val totalAmount: Long,
    val discountAmount: Long,
    val finalAmount: Long,
    val appliedCoupon: AppliedCouponInfo?
) {
    companion object {
        fun from(result: PricingInfoDto): PricingInfo {
            return PricingInfo(
                totalAmount = result.totalAmount,
                discountAmount = result.discountAmount,
                finalAmount = result.finalAmount,
                appliedCoupon = result.appliedCoupon?.let { AppliedCouponInfo.from(it) }
            )
        }
    }
}

data class AppliedCouponInfo(
    val couponId: UUID,
    val couponName: String,
    val discountRate: Int
) {
    companion object {
        fun from(result: AppliedCouponInfoDto): AppliedCouponInfo {
            return AppliedCouponInfo(
                couponId = result.couponId,
                couponName = result.couponName,
                discountRate = result.discountRate
            )
        }
    }
}