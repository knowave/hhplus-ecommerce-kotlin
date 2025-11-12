package com.hhplus.ecommerce.application.user.dto

import com.hhplus.ecommerce.domain.user.entity.User
import java.time.LocalDateTime
import java.util.UUID

data class UserResult (
    val id: UUID,
    val balance: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(user: User): UserResult {
            return UserResult(
                id = user.id!!,
                balance = user.balance,
                createdAt = user.createdAt!!,
                updatedAt = user.updatedAt!!
            )
        }
    }
}