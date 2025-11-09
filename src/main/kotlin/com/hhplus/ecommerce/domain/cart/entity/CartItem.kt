package com.hhplus.ecommerce.domain.cart.entity

import java.time.LocalDateTime

data class CartItem(
    val id: Long,
    val userId: Long,
    val productId: Long,
    var quantity: Int,
    val addedAt: LocalDateTime,
    var updatedAt: LocalDateTime = addedAt
)