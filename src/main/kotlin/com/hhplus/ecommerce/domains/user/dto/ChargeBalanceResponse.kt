package com.hhplus.ecommerce.domains.user.dto

data class ChargeBalanceResponse(
    val userId: Long,
    val previousBalance: Long,
    val chargedAmount: Long,
    val currentBalance: Long,
    val chargedAt: String
)