package com.hhplus.ecommerce.presentation.user.dto

data class UserInfoResponse(
    val userId: Long,
    val balance: Long,
    val createdAt: String,
    val updatedAt: String
)