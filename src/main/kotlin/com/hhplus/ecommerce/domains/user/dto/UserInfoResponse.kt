package com.hhplus.ecommerce.domains.user.dto

data class UserInfoResponse(
    val userId: Long,
    val balance: Long,
    val createdAt: String,
    val updatedAt: String
)