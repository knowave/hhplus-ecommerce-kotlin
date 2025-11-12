package com.hhplus.ecommerce.domain.cart.repository

import com.hhplus.ecommerce.domain.cart.entity.CartItem
import java.util.UUID

/**
 * 장바구니 데이터 접근 인터페이스
 */
interface CartRepository {
    fun findByUserId(userId: UUID): List<CartItem>

    fun findById(cartItemId: UUID): CartItem?

    fun findByUserIdAndProductId(userId: UUID, productId: UUID): CartItem?

    fun save(cartItem: CartItem): CartItem

    fun delete(cartItemId: UUID)

    fun deleteByUserId(userId: UUID)

    fun findByUserIdAndProductIds(userId: UUID, productIds: List<UUID>): List<CartItem>?

    fun deleteManyByIds(cartItemIds: List<UUID>)
}