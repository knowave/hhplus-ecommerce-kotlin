package com.hhplus.ecommerce.application.user.dto

import com.hhplus.ecommerce.presentation.user.dto.CreateUserRequest

data class CreateUserCommand (
    val balance: Long
) {
    companion object {
        fun command(request: CreateUserRequest): CreateUserCommand {
            return CreateUserCommand(
                balance = request.balance
            )
        }
    }
}