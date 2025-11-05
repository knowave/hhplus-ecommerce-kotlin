package com.hhplus.ecommerce.presentation.payment.dto

import com.hhplus.ecommerce.application.payment.dto.*

/**
 * 결제 취소 응답 DTO
 */
data class CancelPaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
    val refundedAmount: Long,
    val paymentStatus: String,
    val orderStatus: String,
    val balance: RefundBalanceInfo,
    val cancelledAt: String
) {
    companion object {
        fun from(result: CancelPaymentResult): CancelPaymentResponse {
            return CancelPaymentResponse(
                paymentId =  result.paymentId,
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                userId = result.userId,
                refundedAmount = result.refundedAmount,
                paymentStatus = result.paymentStatus,
                orderStatus = result.orderStatus,
                balance = RefundBalanceInfo.from(result.balance),
                cancelledAt = result.cancelledAt
            )
        }
    }
}

/**
 * 환불 잔액 정보
 */
data class RefundBalanceInfo(
    val previousBalance: Long,
    val refundedAmount: Long,
    val currentBalance: Long
) {
    companion object {
        fun from(result: RefundBalanceInfoResult): RefundBalanceInfo {
            return RefundBalanceInfo(
                previousBalance = result.previousBalance,
                refundedAmount = result.refundedAmount,
                currentBalance = result.currentBalance
            )
        }
    }
}