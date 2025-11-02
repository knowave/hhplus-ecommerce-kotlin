package com.hhplus.ecommerce.presentation.cart

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.presentation.cart.dto.AddCartItemRequest
import com.hhplus.ecommerce.presentation.cart.dto.AddCartItemResponse
import com.hhplus.ecommerce.presentation.cart.dto.CartResponse
import com.hhplus.ecommerce.presentation.cart.dto.UpdateCartItemRequest
import com.hhplus.ecommerce.presentation.cart.dto.UpdateCartItemResponse
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("carts")
class CartController(
    private val cartService: CartService
) {

    @Operation(summary = "장바구니 조회", description = "사용자 ID로 장바구니 정보를 조회합니다")
    @GetMapping("/{userId}")
    fun getCart(@PathVariable userId: Long): ResponseEntity<CartResponse> {
        val response = cartService.getCart(userId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "장바구니 상품 추가", description = "사용자의 장바구니에 상품을 추가합니다")
    @PostMapping("/{userId}/items")
    fun addCartItem(
        @PathVariable userId: Long,
        @RequestBody request: AddCartItemRequest
    ): ResponseEntity<AddCartItemResponse> {
        val response = cartService.addCartItem(userId, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "장바구니 상품 수정", description = "장바구니에 담긴 상품의 수량을 수정합니다")
    @PatchMapping("/{userId}/items/{cartItemId}")
    fun updateCartItem(
        @PathVariable userId: Long,
        @PathVariable cartItemId: Long,
        @RequestBody request: UpdateCartItemRequest
    ): ResponseEntity<UpdateCartItemResponse> {
        val response = cartService.updateCartItem(userId, cartItemId, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 특정 상품을 삭제합니다")
    @DeleteMapping("/{userId}/items/{cartItemId}")
    fun deleteCartItem(
        @PathVariable userId: Long,
        @PathVariable cartItemId: Long
    ): ResponseEntity<Void> {
        cartService.deleteCartItem(userId, cartItemId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "장바구니 전체 비우기", description = "사용자의 장바구니를 전체 비웁니다")
    @DeleteMapping("/{userId}")
    fun clearCart(@PathVariable userId: Long): ResponseEntity<Void> {
        cartService.clearCart(userId)
        return ResponseEntity.noContent().build()
    }
}