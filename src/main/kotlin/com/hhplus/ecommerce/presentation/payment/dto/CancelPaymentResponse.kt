package com.hhplus.ecommerce.presentation.payment.dto

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
)

/**
 * 환불 잔액 정보
 */
data class RefundBalanceInfo(
    val previousBalance: Long,
    val refundedAmount: Long,
    val currentBalance: Long
)