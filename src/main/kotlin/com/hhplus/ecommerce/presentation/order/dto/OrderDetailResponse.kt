package com.hhplus.ecommerce.presentation.order.dto

import com.hhplus.ecommerce.application.order.dto.OrderDetailResult
import com.hhplus.ecommerce.application.order.dto.PaymentInfoDto
import java.util.UUID

/**
 * 주문 상세 조회 응답 DTO
 */
data class OrderDetailResponse(
    val orderId: UUID,
    val userId: UUID,
    val orderNumber: String,
    val items: List<OrderItemResponse>,
    val pricing: PricingInfo,
    val status: String,
    val payment: PaymentInfo?,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(result: OrderDetailResult): OrderDetailResponse {
            return OrderDetailResponse(
                orderId = result.orderId,
                userId = result.userId,
                orderNumber = result.orderNumber,
                items = result.items.map { OrderItemResponse.from(it) },
                pricing = PricingInfo.from(result.pricing),
                status = result.status,
                payment = result.payment?.let { PaymentInfo.from(it) },
                createdAt = result.createdAt,
                updatedAt = result.updatedAt
            )
        }
    }
}

data class PaymentInfo(
    val paidAmount: Long,
    val paidAt: String
) {
    companion object {
        fun from(result: PaymentInfoDto): PaymentInfo {
            return PaymentInfo(
                paidAmount = result.paidAmount,
                paidAt = result.paidAt
            )
        }
    }
}