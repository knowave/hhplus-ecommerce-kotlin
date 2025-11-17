package com.hhplus.ecommerce.presentation.payment.dto

import com.hhplus.ecommerce.application.payment.dto.*
import java.util.UUID

/**
 * 주문별 결제 내역 조회 응답 DTO
 * GET /api/orders/{orderId}/payment
 */
data class OrderPaymentResponse(
    val orderId: UUID,
    val orderNumber: String,
    val payment: PaymentInfo?
) {
    companion object {
        fun from(result: OrderPaymentResult): OrderPaymentResponse {
            return OrderPaymentResponse(
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                payment = result.payment?.let { PaymentInfo.from(it) }
            )
        }
    }
}

data class PaymentInfo(
    val paymentId: UUID,
    val amount: Long,
    val status: String,
    val paidAt: String
) {
    companion object {
        fun from(result: PaymentInfoResult): PaymentInfo {
            return PaymentInfo(
                paymentId = result.paymentId,
                amount = result.amount,
                status = result.status,
                paidAt = result.paidAt
            )
        }
    }
}
