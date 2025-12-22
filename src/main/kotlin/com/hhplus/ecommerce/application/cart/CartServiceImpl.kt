package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.application.cart.dto.*
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.cart.repository.CartJpaRepository
import com.hhplus.ecommerce.domain.cart.entity.CartItem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Profile("!load-test")
class CartServiceImpl(
    private val cartRepository: CartJpaRepository,
    private val productService: ProductService,
    private val userService: UserService
) : CartService {

    companion object {
        private const val MAX_QUANTITY_PER_ITEM = 100
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun getCart(userId: UUID): CartResult {
        // 사용자 존재 확인
        validateUserExists(userId)

        // 사용자의 장바구니 아이템 조회
        val cartItems = cartRepository.findByUserId(userId)

        if (cartItems.isEmpty()) {
            return CartResult(
                userId = userId,
                items = emptyList(),
                summary = CartSummaryResult(
                    totalItems = 0,
                    totalQuantity = 0,
                    totalAmount = 0L,
                    availableAmount = 0L,
                    unavailableCount = 0
                )
            )
        }

        // 상품 정보와 함께 응답 생성
        val itemResponses = cartItems.map { cartItem ->
            val product = productService.findProductById(cartItem.productId)

            val isAvailable = product.stock > 0
            val subtotal = product.price * cartItem.quantity

            CartItemResult(
                cartItemId = cartItem.id!!,
                productId = product.id!!,
                productName = product.name,
                price = product.price,
                quantity = cartItem.quantity,
                subtotal = subtotal,
                stock = product.stock,
                isAvailable = isAvailable,
                addedAt = cartItem.createdAt!!.format(DATE_FORMATTER)
            )
        }

        // 요약 정보 계산
        val summary = calculateSummary(itemResponses)

        return CartResult(
            userId = userId,
            items = itemResponses,
            summary = summary
        )
    }

    override fun addCartItem(userId: UUID, request: AddCartItemCommand): AddCartItemResult {
        // 사용자 존재 확인
        validateUserExists(userId)

        // 수량 유효성 검증
        validateQuantity(request.quantity)

        // 상품 조회 및 검증
        val product = productService.findProductById(request.productId)

        // 품절 상품 확인
        if (product.stock == 0) {
            throw InsufficientStockException(
                productId = product.id!!,
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
            validateStock(product.id!!, newQuantity, product.stock)

            // 수량 업데이트
            existingItem.quantity = newQuantity
            cartRepository.save(existingItem)
        } else {
            // 새 아이템 추가
            validateMaxQuantity(request.quantity)
            validateStock(product.id!!, request.quantity, product.stock)

            val newItem = CartItem(
                userId = userId,
                productId = request.productId,
                quantity = request.quantity
            )
            cartRepository.save(newItem)
        }

        val subtotal = product.price * cartItem.quantity

        val targetCartItem = cartItem ?: existingItem
        ?: throw InvalidCartItemException("cartItem and existing cart item is null")

        return AddCartItemResult(
            cartItemId = targetCartItem.id!!,
            productId = product.id!!,
            productName = product.name,
            price = product.price,
            quantity = targetCartItem.quantity,
            subtotal = subtotal,
            addedAt = targetCartItem.createdAt!!.format(DATE_FORMATTER)
        )
    }

    override fun updateCartItem(
        userId: UUID,
        cartItemId: UUID,
        request: UpdateCartItemCommand
    ): UpdateCartItemResult {
        // 장바구니 아이템 조회
        val cartItem = cartRepository.findById(cartItemId)
            .orElseThrow{ CartItemNotFoundException(cartItemId) }

        // 권한 확인 (다른 사용자의 장바구니 아이템인지)
        if (cartItem.userId != userId) {
            throw ForbiddenException("다른 사용자의 장바구니 아이템입니다.")
        }

        // 수량이 0이면 삭제
        if (request.quantity == 0) {
            cartRepository.delete(cartItem)
            throw CartItemNotFoundException(cartItemId)
        }

        // 수량 유효성 검증
        validateQuantity(request.quantity)
        validateMaxQuantity(request.quantity)

        // 상품 조회 및 재고 확인
        val product = productService.findProductById(cartItem.productId)

        validateStock(product.id!!, request.quantity, product.stock)

        // 수량 업데이트
        cartItem.quantity = request.quantity
        cartRepository.save(cartItem)

        val subtotal = product.price * cartItem.quantity

        return UpdateCartItemResult(
            cartItemId = cartItem.id!!,
            productId = product.id!!,
            productName = product.name,
            price = product.price,
            quantity = cartItem.quantity,
            subtotal = subtotal,
            updatedAt = cartItem.updatedAt!!.format(DATE_FORMATTER)
        )
    }

    override fun deleteCartItem(userId: UUID, cartItemId: UUID) {
        // 장바구니 아이템 조회
        val cartItem = cartRepository.findById(cartItemId)
            .orElseThrow { CartItemNotFoundException(cartItemId) }

        // 권한 확인
        if (cartItem.userId != userId) {
            throw ForbiddenException("access denied cart item")
        }

        // 삭제
        cartRepository.delete(cartItem)
    }

    override fun clearCart(userId: UUID) {
        // 사용자 존재 확인
        validateUserExists(userId)

        // 장바구니 전체 삭제
        cartRepository.deleteByUserId(userId)
    }

    override fun deleteCarts(userId: UUID, productIds: List<UUID>) {
        val cartItems = cartRepository.findByUserIdAndProductIdIn(userId, productIds)

        val cartItemIds = cartItems.map { it.id }
        if (cartItemIds.isNotEmpty()) {
            cartRepository.deleteAllById(cartItemIds)
        }
    }

    // --- Private Helper Methods ---

    /**
     * 사용자 존재 여부 확인
     */
    private fun validateUserExists(userId: UUID) {
        userService.getUser(userId)
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
    private fun validateStock(productId: UUID, requested: Int, available: Int) {
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
    private fun calculateSummary(items: List<CartItemResult>): CartSummaryResult {
        val totalItems = items.size
        val totalQuantity = items.sumOf { it.quantity }
        val totalAmount = items.sumOf { it.subtotal }
        val availableAmount = items.filter { it.isAvailable }.sumOf { it.subtotal }
        val unavailableCount = items.count { !it.isAvailable }

        return CartSummaryResult(
            totalItems = totalItems,
            totalQuantity = totalQuantity,
            totalAmount = totalAmount,
            availableAmount = availableAmount,
            unavailableCount = unavailableCount
        )
    }
}