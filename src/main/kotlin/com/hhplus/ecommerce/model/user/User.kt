package com.hhplus.ecommerce.model.user

data class User(
    val id: Long,
    var balance: Long,
    val createdAt: String,
    var updatedAt: String
)