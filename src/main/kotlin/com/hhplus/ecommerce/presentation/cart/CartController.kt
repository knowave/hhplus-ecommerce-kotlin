package com.hhplus.ecommerce.presentation.cart

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.cart.dto.AddCartItemCommand
import com.hhplus.ecommerce.application.cart.dto.UpdateCartItemCommand
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
import java.util.UUID

@RestController
@RequestMapping("carts")
class CartController(
    private val cartService: CartService
) {

    @Operation(summary = "장바구니 조회", description = "사용자 ID로 장바구니 정보를 조회합니다")
    @GetMapping("/{userId}")
    fun getCart(@PathVariable userId: UUID): ResponseEntity<CartResponse> {
        val result = cartService.getCart(userId)
        return ResponseEntity.ok(CartResponse.from(result))
    }

    @Operation(summary = "장바구니 상품 추가", description = "사용자의 장바구니에 상품을 추가합니다")
    @PostMapping("/{userId}/items")
    fun addCartItem(
        @PathVariable userId: UUID,
        @RequestBody request: AddCartItemRequest
    ): ResponseEntity<AddCartItemResponse> {
        val command = AddCartItemCommand.command(request)
        val result = cartService.addCartItem(userId, command)

        return ResponseEntity.ok(AddCartItemResponse.from(result))
    }

    @Operation(summary = "장바구니 상품 수정", description = "장바구니에 담긴 상품의 수량을 수정합니다")
    @PatchMapping("/{userId}/items/{cartItemId}")
    fun updateCartItem(
        @PathVariable userId: UUID,
        @PathVariable cartItemId: UUID,
        @RequestBody request: UpdateCartItemRequest
    ): ResponseEntity<UpdateCartItemResponse> {
        val command = UpdateCartItemCommand.command(request)
        val result = cartService.updateCartItem(userId, cartItemId, command)

        return ResponseEntity.ok(UpdateCartItemResponse.from(result))
    }

    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 특정 상품을 삭제합니다")
    @DeleteMapping("/{userId}/items/{cartItemId}")
    fun deleteCartItem(
        @PathVariable userId: UUID,
        @PathVariable cartItemId: UUID
    ): ResponseEntity<Void> {
        cartService.deleteCartItem(userId, cartItemId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "장바구니 전체 비우기", description = "사용자의 장바구니를 전체 비웁니다")
    @DeleteMapping("/{userId}")
    fun clearCart(@PathVariable userId: UUID): ResponseEntity<Void> {
        cartService.clearCart(userId)
        return ResponseEntity.noContent().build()
    }
}