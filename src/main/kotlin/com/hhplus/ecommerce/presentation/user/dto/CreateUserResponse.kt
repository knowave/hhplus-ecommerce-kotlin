package com.hhplus.ecommerce.presentation.user.dto

import com.hhplus.ecommerce.domain.user.entity.User
import java.time.LocalDateTime
import java.util.UUID

data class CreateUserResponse(
    val id: UUID,
    var balance: Long,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime
) {
    companion object {
        fun from(user: User): CreateUserResponse {
            return CreateUserResponse(
                id = user.id!!,
                balance = user.balance,
                createdAt = user.createdAt!!,
                updatedAt = user.updatedAt!!
            )
        }
    }
}
