package com.hhplus.ecommerce.infrastructure.cart

import com.hhplus.ecommerce.domain.cart.entity.CartItem
import com.hhplus.ecommerce.domain.cart.repository.CartRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CartRepositoryImpl : CartRepository {

    // Mock 데이터 저장소: CartItem
    private val cartItems: MutableMap<UUID, CartItem> = mutableMapOf()

    private fun assignId(cart: CartItem) {
        if (cart.id == null) {
            val idField = cart.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(cart, UUID.randomUUID())
        }
    }

    override fun findById(cartItemId: UUID): CartItem? {
        return cartItems[cartItemId]
    }

    override fun findByUserIdAndProductId(userId: UUID, productId: UUID): CartItem? {
        return cartItems.values
            .firstOrNull { it.userId == userId && it.productId == productId }
    }

    override fun save(cartItem: CartItem): CartItem {
        assignId(cartItem)
        cartItems[cartItem.id!!] = cartItem
        return cartItem
    }

    override fun delete(cartItemId: UUID) {
        cartItems.remove(cartItemId)
    }

    override fun deleteByUserId(userId: UUID) {
        val userCartItems = cartItems.values
            .filter { it.userId == userId }
            .map { it.id }

        userCartItems.forEach { cartItems.remove(it) }
    }

    override fun findByUserIdAndProductIds(userId: UUID, productIds: List<UUID>): List<CartItem>? {
        return cartItems.values
            .filter { it.userId == userId && it.productId in productIds }
            .sortedBy { it.createdAt }
    }

    override fun deleteManyByIds(cartItemIds: List<UUID>) {
        cartItemIds.forEach { cartItems.remove(it) }
    }

    override fun findByUserId(userId: UUID): List<CartItem> {
        return cartItems.values
            .filter { it.userId === userId }
            .sortedBy { it.createdAt }
    }
}