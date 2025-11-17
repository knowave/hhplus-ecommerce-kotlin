package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.domain.user.entity.User
import java.util.UUID

data class UserBalanceResponse(
    val userId: UUID,
    val balance: Long,
) {
    companion object {
        fun from(user: User): UserBalanceResponse {
            return UserBalanceResponse(
                userId =  user.id!!,
                balance = user.balance
            )
        }
    }
}