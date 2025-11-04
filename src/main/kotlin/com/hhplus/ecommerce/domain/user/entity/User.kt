package com.hhplus.ecommerce.domain.user.entity

data class User(
    val id: Long,
    var balance: Long,
    val createdAt: String,
    var updatedAt: String
)