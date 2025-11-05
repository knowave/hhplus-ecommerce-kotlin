package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.domain.user.entity.User

data class UserBalanceResponse(
    val userId: Long,
    val balance: Long,
) {
    companion object {
        fun from(user: User): UserBalanceResponse {
            return UserBalanceResponse(
                userId =  user.id,
                balance = user.balance
            )
        }
    }
}