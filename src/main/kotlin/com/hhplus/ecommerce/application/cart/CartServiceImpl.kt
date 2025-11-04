package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.common.exception.CartItemNotFoundException
import com.hhplus.ecommerce.common.exception.ExceedMaxQuantityException
import com.hhplus.ecommerce.common.exception.ForbiddenException
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.common.exception.InvalidQuantityException
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.cart.CartRepository
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.presentation.cart.dto.AddCartItemRequest
import com.hhplus.ecommerce.presentation.cart.dto.AddCartItemResponse
import com.hhplus.ecommerce.domain.cart.entity.CartItem
import com.hhplus.ecommerce.presentation.cart.dto.CartItemResponse
import com.hhplus.ecommerce.presentation.cart.dto.CartResponse
import com.hhplus.ecommerce.presentation.cart.dto.CartSummary
import com.hhplus.ecommerce.presentation.cart.dto.UpdateCartItemRequest
import com.hhplus.ecommerce.presentation.cart.dto.UpdateCartItemResponse
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 장바구니 서비스 구현체
 *
 * 이유: Cart API 문서에 정의된 비즈니스 로직을 구현합니다.
 * UserServiceImpl과 동일한 패턴을 따라 구현하여 일관성을 유지합니다.
 */
@Service
class CartServiceImpl(
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) : CartService {

    companion object {
        private const val MAX_QUANTITY_PER_ITEM = 100
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun getCart(userId: Long): CartResponse {
        // 사용자 존재 확인
        validateUserExists(userId)

        // 사용자의 장바구니 아이템 조회
        val cartItems = cartRepository.findByUserId(userId)

        // 상품 정보와 함께 응답 생성
        val itemResponses = cartItems.map { cartItem ->
            val product = productRepository.findById(cartItem.productId)
                ?: throw ProductNotFoundException(cartItem.productId)

            val isAvailable = product.stock > 0
            val subtotal = product.price * cartItem.quantity

            CartItemResponse(
                cartItemId = cartItem.id,
                productId = product.id,
                productName = product.name,
                price = product.price,
                quantity = cartItem.quantity,
                subtotal = subtotal,
                stock = product.stock,
                isAvailable = isAvailable,
                addedAt = cartItem.addedAt.format(DATE_FORMATTER)
            )
        }

        // 요약 정보 계산
        val summary = calculateSummary(itemResponses)

        return CartResponse(
            userId = userId,
            items = itemResponses,
            summary = summary
        )
    }

    override fun addCartItem(userId: Long, request: AddCartItemRequest): AddCartItemResponse {
        // 사용자 존재 확인
        validateUserExists(userId)

        // 수량 유효성 검증
        validateQuantity(request.quantity)

        // 상품 조회 및 검증
        val product = productRepository.findById(request.productId)
            ?: throw ProductNotFoundException(request.productId)

        // 품절 상품 확인
        if (product.stock == 0) {
            throw InsufficientStockException(
                productId = product.id,
                requested = request.quantity,
                available = 0
            )
        }

        // 기존 장바구니 아이템 확인
        val existingItem = cartRepository.findByUserIdAndProductId(userId, request.productId)

        val cartItem = if (existingItem != null) {
            // 기존 아이템이 있으면 수량 합산
            val newQuantity = existingItem.quantity + request.quantity

            // 최대 수량 체크
            validateMaxQuantity(newQuantity)

            // 재고 체크
            validateStock(product.id, newQuantity, product.stock)

            // 수량 업데이트
            existingItem.quantity = newQuantity
            existingItem.updatedAt = LocalDateTime.now()
            cartRepository.save(existingItem)
        } else {
            // 새 아이템 추가
            validateMaxQuantity(request.quantity)
            validateStock(product.id, request.quantity, product.stock)

            val newItem = CartItem(
                id = cartRepository.generateId(),
                userId = userId,
                productId = request.productId,
                quantity = request.quantity,
                addedAt = LocalDateTime.now()
            )
            cartRepository.save(newItem)
        }

        val subtotal = product.price * cartItem.quantity

        return AddCartItemResponse(
            cartItemId = cartItem.id,
            productId = product.id,
            productName = product.name,
            price = product.price,
            quantity = cartItem.quantity,
            subtotal = subtotal,
            addedAt = cartItem.addedAt.format(DATE_FORMATTER)
        )
    }

    override fun updateCartItem(
        userId: Long,
        cartItemId: Long,
        request: UpdateCartItemRequest
    ): UpdateCartItemResponse {
        // 장바구니 아이템 조회
        val cartItem = cartRepository.findById(cartItemId)
            ?: throw CartItemNotFoundException(cartItemId)

        // 권한 확인 (다른 사용자의 장바구니 아이템인지)
        if (cartItem.userId != userId) {
            throw ForbiddenException("다른 사용자의 장바구니 아이템입니다.")
        }

        // 수량이 0이면 삭제
        if (request.quantity == 0) {
            cartRepository.delete(cartItemId)
            throw CartItemNotFoundException(cartItemId)
        }

        // 수량 유효성 검증
        validateQuantity(request.quantity)
        validateMaxQuantity(request.quantity)

        // 상품 조회 및 재고 확인
        val product = productRepository.findById(cartItem.productId)
            ?: throw ProductNotFoundException(cartItem.productId)

        validateStock(product.id, request.quantity, product.stock)

        // 수량 업데이트
        cartItem.quantity = request.quantity
        cartItem.updatedAt = LocalDateTime.now()
        cartRepository.save(cartItem)

        val subtotal = product.price * cartItem.quantity

        return UpdateCartItemResponse(
            cartItemId = cartItem.id,
            productId = product.id,
            productName = product.name,
            price = product.price,
            quantity = cartItem.quantity,
            subtotal = subtotal,
            updatedAt = cartItem.updatedAt.format(DATE_FORMATTER)
        )
    }

    override fun deleteCartItem(userId: Long, cartItemId: Long) {
        // 장바구니 아이템 조회
        val cartItem = cartRepository.findById(cartItemId)
            ?: throw CartItemNotFoundException(cartItemId)

        // 권한 확인
        if (cartItem.userId != userId) {
            throw ForbiddenException("다른 사용자의 장바구니 아이템입니다.")
        }

        // 삭제
        cartRepository.delete(cartItemId)
    }

    override fun clearCart(userId: Long) {
        // 사용자 존재 확인
        validateUserExists(userId)

        // 장바구니 전체 삭제
        cartRepository.deleteByUserId(userId)
    }

    // --- Private Helper Methods ---

    /**
     * 사용자 존재 여부 확인
     */
    private fun validateUserExists(userId: Long) {
        userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)
    }

    /**
     * 수량 유효성 검증 (1 이상)
     */
    private fun validateQuantity(quantity: Int) {
        if (quantity < 1) {
            throw InvalidQuantityException(quantity)
        }
    }

    /**
     * 최대 수량 검증 (100개 이하)
     */
    private fun validateMaxQuantity(quantity: Int) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw ExceedMaxQuantityException(
                maxQuantity = MAX_QUANTITY_PER_ITEM,
                attempted = quantity
            )
        }
    }

    /**
     * 재고 확인
     */
    private fun validateStock(productId: Long, requested: Int, available: Int) {
        if (requested > available) {
            throw InsufficientStockException(
                productId = productId,
                requested = requested,
                available = available
            )
        }
    }

    /**
     * 장바구니 요약 정보 계산
     */
    private fun calculateSummary(items: List<CartItemResponse>): CartSummary {
        val totalItems = items.size
        val totalQuantity = items.sumOf { it.quantity }
        val totalAmount = items.sumOf { it.subtotal }
        val availableAmount = items.filter { it.isAvailable }.sumOf { it.subtotal }
        val unavailableCount = items.count { !it.isAvailable }

        return CartSummary(
            totalItems = totalItems,
            totalQuantity = totalQuantity,
            totalAmount = totalAmount,
            availableAmount = availableAmount,
            unavailableCount = unavailableCount
        )
    }
}