package com.hhplus.ecommerce.model.cart

import java.time.LocalDateTime

/**
 * 장바구니 아이템 도메인 모델
 * 인메모리 저장소에서 사용되는 내부 데이터 구조
 */
data class CartItem(
    val cartItemId: Long,
    val userId: Long,
    val productId: Long,
    var quantity: Int,
    val addedAt: LocalDateTime,
    var updatedAt: LocalDateTime = addedAt
)