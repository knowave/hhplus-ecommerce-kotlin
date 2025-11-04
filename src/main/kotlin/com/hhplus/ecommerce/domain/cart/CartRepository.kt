package com.hhplus.ecommerce.domain.cart

import com.hhplus.ecommerce.domain.cart.entity.CartItem

/**
 * 장바구니 데이터 접근 인터페이스
 */
interface CartRepository {

    /**
     * 사용자의 모든 장바구니 아이템 조회
     */
    fun findByUserId(userId: Long): List<CartItem>

    /**
     * 장바구니 아이템 ID로 조회
     */
    fun findById(cartItemId: Long): CartItem?

    /**
     * 특정 사용자의 특정 상품 장바구니 아이템 조회
     */
    fun findByUserIdAndProductId(userId: Long, productId: Long): CartItem?

    /**
     * 장바구니 아이템 저장 (추가 또는 수정)
     */
    fun save(cartItem: CartItem): CartItem

    /**
     * 장바구니 아이템 삭제
     */
    fun delete(cartItemId: Long)

    /**
     * 사용자의 모든 장바구니 아이템 삭제
     */
    fun deleteByUserId(userId: Long)

    /**
     * 장바구니 아이템 ID 생성
     */
    fun generateId(): Long
}