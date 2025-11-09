package com.hhplus.ecommerce.application.cart.dto

import com.hhplus.ecommerce.presentation.cart.dto.CartItemResponse
import com.hhplus.ecommerce.presentation.cart.dto.CartSummary

data class CartSummaryResult(
    val totalItems: Int,         // 총 아이템 종류 수
    val totalQuantity: Int,      // 총 수량
    val totalAmount: Long,       // 총 금액
    val availableAmount: Long,   // 구매 가능한 상품 금액
    val unavailableCount: Int    // 품절 상품 수
)

data class CartItemResult(
    val cartItemId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
    val stock: Int,
    val isAvailable: Boolean,
    val addedAt: String
)

data class CartResult(
    val userId: Long,
    val items: List<CartItemResult>,
    val summary: CartSummaryResult
)
