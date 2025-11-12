package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.application.user.dto.ChargeBalanceResult
import java.util.UUID

data class ChargeBalanceResponse(
    val userId: UUID,
    val previousBalance: Long,
    val chargedAmount: Long,
    val currentBalance: Long,
    val chargedAt: String
) {
    companion object{
        fun from(result: ChargeBalanceResult): ChargeBalanceResponse {
            return ChargeBalanceResponse(
                userId = result.userId,
                previousBalance = result.previousBalance,
                chargedAmount = result.chargedAmount,
                currentBalance = result.currentBalance,
                chargedAt = result.chargedAt
            )
        }
    }
}