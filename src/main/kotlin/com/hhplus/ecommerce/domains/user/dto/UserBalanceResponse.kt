package com.hhplus.ecommerce.domains.user.dto

data class UserBalanceResponse(
    val userId: Long,
    val balance: Long,
)