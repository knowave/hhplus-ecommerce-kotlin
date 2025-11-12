package com.hhplus.ecommerce.application.user.dto

import java.util.UUID

data class ChargeBalanceResult(
    val userId: UUID,
    val previousBalance: Long,
    val chargedAmount: Long,
    val currentBalance: Long,
    val chargedAt: String
)