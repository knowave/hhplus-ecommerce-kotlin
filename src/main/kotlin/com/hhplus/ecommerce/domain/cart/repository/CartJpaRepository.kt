package com.hhplus.ecommerce.domain.cart.repository

import com.hhplus.ecommerce.domain.cart.entity.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CartJpaRepository : JpaRepository<CartItem, UUID> {
    fun findByUserId(userId: UUID): List<CartItem>

    fun findByUserIdAndProductId(userId: UUID, productId: UUID): CartItem?

    fun deleteByUserId(userId: UUID): Long

    fun findByUserIdAndProductIdIn(userId: UUID, productIds: List<UUID>): List<CartItem>
}