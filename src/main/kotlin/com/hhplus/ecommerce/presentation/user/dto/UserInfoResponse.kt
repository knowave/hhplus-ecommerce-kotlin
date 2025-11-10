package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.application.user.dto.UserResult
import java.time.LocalDateTime
import java.util.UUID

data class UserInfoResponse(
    val userId: UUID,
    val balance: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(user: UserResult): UserInfoResponse {
            return UserInfoResponse(
                userId = user.id,
                balance = user.balance,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }
    }
}