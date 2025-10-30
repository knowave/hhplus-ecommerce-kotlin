package com.hhplus.ecommerce.domains.cart

import com.hhplus.ecommerce.domains.cart.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/carts")
class CartController(
    private val cartService: CartService
) {

    @GetMapping("/{userId}")
    fun getCart(@PathVariable userId: Long): ResponseEntity<CartResponse> {
        val response = cartService.getCart(userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{userId}/items")
    fun addCartItem(
        @PathVariable userId: Long,
        @RequestBody request: AddCartItemRequest
    ): ResponseEntity<AddCartItemResponse> {
        val response = cartService.addCartItem(userId, request)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{userId}/items/{cartItemId}")
    fun updateCartItem(
        @PathVariable userId: Long,
        @PathVariable cartItemId: Long,
        @RequestBody request: UpdateCartItemRequest
    ): ResponseEntity<UpdateCartItemResponse> {
        val response = cartService.updateCartItem(userId, cartItemId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{userId}/items/{cartItemId}")
    fun deleteCartItem(
        @PathVariable userId: Long,
        @PathVariable cartItemId: Long
    ): ResponseEntity<Void> {
        cartService.deleteCartItem(userId, cartItemId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{userId}")
    fun clearCart(@PathVariable userId: Long): ResponseEntity<Void> {
        cartService.clearCart(userId)
        return ResponseEntity.noContent().build()
    }
}