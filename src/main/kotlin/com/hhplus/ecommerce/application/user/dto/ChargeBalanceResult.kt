package com.hhplus.ecommerce.application.user.dto

data class ChargeBalanceResult(
    val userId: Long,
    val previousBalance: Long,
    val chargedAmount: Long,
    val currentBalance: Long,
    val chargedAt: String
)