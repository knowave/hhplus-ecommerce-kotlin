package com.hhplus.ecommerce.presentation.payment.dto

import com.hhplus.ecommerce.application.payment.dto.BalanceInfoResult
import com.hhplus.ecommerce.application.payment.dto.DataTransmissionInfoResult
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentResult

/**
 * 결제 처리 응답 DTO
 */
data class ProcessPaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val userId: Long,
    val amount: Long,
    val paymentStatus: String,
    val orderStatus: String,
    val balance: BalanceInfo,
    val dataTransmission: DataTransmissionInfo,
    val paidAt: String
) {
    companion object {
        fun from(result: ProcessPaymentResult): ProcessPaymentResponse {
            return ProcessPaymentResponse(
                paymentId = result.paymentId,
                orderId = result.orderId,
                orderNumber = result.orderNumber,
                userId = result.userId,
                amount = result.amount,
                paymentStatus = result.paymentStatus,
                orderStatus = result.orderStatus,
                balance = BalanceInfo.from(result.balance),
                dataTransmission = DataTransmissionInfo.from(result.dataTransmission),
                paidAt =  result.paidAt
            )
        }
    }
}

data class BalanceInfo(
    val previousBalance: Long,
    val paidAmount: Long,
    val remainingBalance: Long
) {
    companion object {
        fun from(result: BalanceInfoResult): BalanceInfo {
            return BalanceInfo(
                previousBalance = result.previousBalance,
                paidAmount = result.paidAmount,
                remainingBalance = result.remainingBalance
            )
        }
    }
}

data class DataTransmissionInfo(
    val transmissionId: Long,
    val status: String,
    val scheduledAt: String
) {
    companion object {
        fun from(result: DataTransmissionInfoResult): DataTransmissionInfo {
            return DataTransmissionInfo(
                transmissionId = result.transmissionId,
                status = result.status,
                scheduledAt = result.scheduledAt
            )
        }
    }
}
