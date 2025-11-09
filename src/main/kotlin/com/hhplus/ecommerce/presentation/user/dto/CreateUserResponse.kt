package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.domain.user.entity.User

data class CreateUserResponse(
    val id: Long,
    var balance: Long,
    val createdAt: String,
    var updatedAt: String
) {
    companion object {
        fun from(user: User): CreateUserResponse {
            return CreateUserResponse(
                id = user.id,
                balance = user.balance,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }
    }
}
