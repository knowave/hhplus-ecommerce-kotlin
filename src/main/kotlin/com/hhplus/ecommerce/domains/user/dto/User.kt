package com.hhplus.ecommerce.domains.user.dto

data class User(
    val id: Long,
    var balance: Long,
    val createdAt: String,
    var updatedAt: String
)