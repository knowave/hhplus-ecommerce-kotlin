package com.hhplus.ecommerce.infrastructure.cart

import com.hhplus.ecommerce.model.cart.CartItem
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 장바구니 인메모리 Repository 구현체
 *
 * 이유: 실제 DB 없이 메모리에서 장바구니 데이터를 관리합니다.
 * UserRepositoryImpl과 동일한 패턴으로 구현하여 일관성을 유지합니다.
 */
@Repository
class CartRepositoryImpl : CartRepository {

    // ID 자동 생성을 위한 카운터
    private var nextId: Long = 1L

    // Mock 데이터 저장소: cartItemId -> CartItem
    private val cartItems: MutableMap<Long, CartItem> = mutableMapOf()

    // 샘플 데이터 초기화
    init {
        // 사용자 1의 장바구니에 샘플 아이템 추가
        val item1 = CartItem(
            cartItemId = generateId(),
            userId = 1L,
            productId = 15L,
            quantity = 2,
            addedAt = LocalDateTime.now().minusHours(5)
        )
        val item2 = CartItem(
            cartItemId = generateId(),
            userId = 1L,
            productId = 7L,
            quantity = 1,
            addedAt = LocalDateTime.now().minusHours(1)
        )
        cartItems[item1.cartItemId] = item1
        cartItems[item2.cartItemId] = item2
    }

    override fun findByUserId(userId: Long): List<CartItem> {
        return cartItems.values
            .filter { it.userId == userId }
            .sortedBy { it.addedAt }
    }

    override fun findById(cartItemId: Long): CartItem? {
        return cartItems[cartItemId]
    }

    override fun findByUserIdAndProductId(userId: Long, productId: Long): CartItem? {
        return cartItems.values
            .firstOrNull { it.userId == userId && it.productId == productId }
    }

    override fun save(cartItem: CartItem): CartItem {
        cartItems[cartItem.cartItemId] = cartItem
        return cartItem
    }

    override fun delete(cartItemId: Long) {
        cartItems.remove(cartItemId)
    }

    override fun deleteByUserId(userId: Long) {
        val userCartItems = cartItems.values
            .filter { it.userId == userId }
            .map { it.cartItemId }

        userCartItems.forEach { cartItems.remove(it) }
    }

    override fun generateId(): Long {
        return nextId++
    }
}