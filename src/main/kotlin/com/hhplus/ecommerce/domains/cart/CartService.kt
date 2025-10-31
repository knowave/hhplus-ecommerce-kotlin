package com.hhplus.ecommerce.domains.cart

import com.hhplus.ecommerce.domains.cart.dto.*

/**
 * 장바구니 비즈니스 로직 인터페이스
 */
interface CartService {

    /**
     * 사용자의 장바구니 조회
     */
    fun getCart(userId: Long): CartResponse

    /**
     * 장바구니에 상품 추가
     */
    fun addCartItem(userId: Long, request: AddCartItemRequest): AddCartItemResponse

    /**
     * 장바구니 아이템 수량 변경
     */
    fun updateCartItem(userId: Long, cartItemId: Long, request: UpdateCartItemRequest): UpdateCartItemResponse

    /**
     * 장바구니 아이템 삭제
     */
    fun deleteCartItem(userId: Long, cartItemId: Long)

    /**
     * 장바구니 전체 비우기
     */
    fun clearCart(userId: Long)
}