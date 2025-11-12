package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.application.cart.dto.*
import java.util.UUID

/**
 * 장바구니 비즈니스 로직 인터페이스
 */
interface CartService {

    /**
     * 사용자의 장바구니 조회
     */
    fun getCart(userId: UUID): CartResult

    /**
     * 장바구니에 상품 추가
     */
    fun addCartItem(userId: UUID, request: AddCartItemCommand): AddCartItemResult

    /**
     * 장바구니 아이템 수량 변경
     */
    fun updateCartItem(userId: UUID, cartItemId: UUID, request: UpdateCartItemCommand): UpdateCartItemResult

    /**
     * 장바구니 아이템 삭제
     */
    fun deleteCartItem(userId: UUID, cartItemId: UUID)

    /**
     * 장바구니 전체 비우기
     */
    fun clearCart(userId: UUID)

    fun deleteCarts(userId: UUID, productIds: List<UUID>)
}