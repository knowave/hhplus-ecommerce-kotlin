package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.domain.user.entity.User

data class UserInfoResponse(
    val userId: Long,
    val balance: Long,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(user: User): UserInfoResponse {
            return UserInfoResponse(
                userId = user.id,
                balance = user.balance,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }
    }
}