package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.cart.CartRepository
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.domain.cart.entity.CartItem
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.presentation.cart.dto.AddCartItemRequest
import com.hhplus.ecommerce.presentation.cart.dto.UpdateCartItemRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CartServiceUnitTest : DescribeSpec({
    lateinit var cartRepository: CartRepository
    lateinit var productRepository: ProductRepository
    lateinit var userRepository: UserRepository
    lateinit var cartService: CartServiceImpl
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        cartRepository = mockk()
        productRepository = mockk()
        userRepository = mockk()
        cartService = CartServiceImpl(cartRepository, productRepository, userRepository)
    }

    describe("CartService 단위 테스트 - getCart") {
        context("정상 케이스") {
            it("빈 장바구니를 조회한다") {
                // given
                val userId = 1L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                every { userRepository.findById(userId) } returns user
                every { cartRepository.findByUserId(userId) } returns emptyList()

                // when
                val result = cartService.getCart(userId)

                // then
                result.userId shouldBe userId
                result.items.shouldBeEmpty()
                result.summary.totalItems shouldBe 0
                result.summary.totalQuantity shouldBe 0
                result.summary.totalAmount shouldBe 0L
                result.summary.availableAmount shouldBe 0L
                result.summary.unavailableCount shouldBe 0

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { cartRepository.findByUserId(userId) }
            }

            it("장바구니에 담긴 상품들을 조회한다") {
                // given
                val userId = 1L
                val now = LocalDateTime.now()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                val product1 = createProduct(15L, "상품15", 50000L, 100, ProductCategory.ELECTRONICS, 0)
                val product2 = createProduct(7L, "상품7", 30000L, 50, ProductCategory.FASHION, 0)

                val cartItems = listOf(
                    CartItem(1L, userId, 15L, 2, now),
                    CartItem(2L, userId, 7L, 1, now)
                )

                every { userRepository.findById(userId) } returns user
                every { cartRepository.findByUserId(userId) } returns cartItems
                every { productRepository.findById(15L) } returns product1
                every { productRepository.findById(7L) } returns product2

                // when
                val result = cartService.getCart(userId)

                // then
                result.userId shouldBe userId
                result.items shouldHaveSize 2

                // 첫 번째 아이템 검증
                result.items[0].cartItemId shouldBe 1L
                result.items[0].productId shouldBe 15L
                result.items[0].productName shouldBe "상품15"
                result.items[0].price shouldBe 50000L
                result.items[0].quantity shouldBe 2
                result.items[0].subtotal shouldBe 100000L
                result.items[0].stock shouldBe 100
                result.items[0].isAvailable shouldBe true

                // 두 번째 아이템 검증
                result.items[1].cartItemId shouldBe 2L
                result.items[1].productId shouldBe 7L
                result.items[1].quantity shouldBe 1
                result.items[1].subtotal shouldBe 30000L

                // 요약 정보 검증
                result.summary.totalItems shouldBe 2
                result.summary.totalQuantity shouldBe 3 // 2 + 1
                result.summary.totalAmount shouldBe 130000L // 100000 + 30000
                result.summary.availableAmount shouldBe 130000L
                result.summary.unavailableCount shouldBe 0

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { cartRepository.findByUserId(userId) }
                verify(exactly = 1) { productRepository.findById(15L) }
                verify(exactly = 1) { productRepository.findById(7L) }
            }

            it("품절된 상품이 포함된 장바구니를 조회한다") {
                // given
                val userId = 1L
                val now = LocalDateTime.now()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                val availableProduct = createProduct(1L, "재고있음", 50000L, 100, ProductCategory.ELECTRONICS, 0)
                val outOfStockProduct = createProduct(2L, "품절상품", 30000L, 0, ProductCategory.FASHION, 0)

                val cartItems = listOf(
                    CartItem(1L, userId, 1L, 2, now),
                    CartItem(2L, userId, 2L, 1, now)
                )

                every { userRepository.findById(userId) } returns user
                every { cartRepository.findByUserId(userId) } returns cartItems
                every { productRepository.findById(1L) } returns availableProduct
                every { productRepository.findById(2L) } returns outOfStockProduct

                // when
                val result = cartService.getCart(userId)

                // then
                result.items shouldHaveSize 2
                result.items[0].isAvailable shouldBe true
                result.items[1].isAvailable shouldBe false
                result.items[1].stock shouldBe 0

                // 요약 정보 검증 - 품절 상품은 제외
                result.summary.totalItems shouldBe 2
                result.summary.totalAmount shouldBe 130000L // 전체 금액
                result.summary.availableAmount shouldBe 100000L // 구매 가능한 금액만
                result.summary.unavailableCount shouldBe 1

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { cartRepository.findByUserId(userId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자로 조회 시 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = 999L
                every { userRepository.findById(invalidUserId) } returns null

                // when & then
                val exception = shouldThrow<UserNotFoundException> {
                    cartService.getCart(invalidUserId)
                }
                exception.message shouldContain "User not found with id: $invalidUserId"

                verify(exactly = 1) { userRepository.findById(invalidUserId) }
                verify(exactly = 0) { cartRepository.findByUserId(any()) }
            }

            it("장바구니에 존재하지 않는 상품이 있으면 ProductNotFoundException을 발생시킨다") {
                // given
                val userId = 1L
                val now = LocalDateTime.now()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                val cartItems = listOf(
                    CartItem(1L, userId, 999L, 2, now) // 존재하지 않는 상품
                )

                every { userRepository.findById(userId) } returns user
                every { cartRepository.findByUserId(userId) } returns cartItems
                every { productRepository.findById(999L) } returns null

                // when & then
                shouldThrow<ProductNotFoundException> {
                    cartService.getCart(userId)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { cartRepository.findByUserId(userId) }
                verify(exactly = 1) { productRepository.findById(999L) }
            }
        }
    }

    describe("CartService 단위 테스트 - addCartItem") {
        context("정상 케이스 - 새로운 상품 추가") {
            it("장바구니에 새 상품을 추가한다") {
                // given
                val userId = 1L
                val productId = 15L
                val quantity = 2
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = quantity)

                val now = LocalDateTime.now()
                val savedCartItem = CartItem(1L, userId, productId, quantity, now)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns null
                every { cartRepository.generateId() } returns 1L
                every { cartRepository.save(any()) } returns savedCartItem

                // when
                val result = cartService.addCartItem(userId, request)

                // then
                result.cartItemId shouldBe 1L
                result.productId shouldBe productId
                result.productName shouldBe "노트북"
                result.price shouldBe 1500000L
                result.quantity shouldBe quantity
                result.subtotal shouldBe 3000000L

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
                verify(exactly = 1) { cartRepository.findByUserIdAndProductId(userId, productId) }
                verify(exactly = 1) { cartRepository.generateId() }
                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }

        context("정상 케이스 - 기존 상품 수량 증가") {
            it("이미 장바구니에 있는 상품의 수량을 증가시킨다") {
                // given
                val userId = 1L
                val productId = 15L
                val existingQuantity = 2
                val additionalQuantity = 3
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = additionalQuantity)

                val now = LocalDateTime.now()
                val existingCartItem = CartItem(1L, userId, productId, existingQuantity, now)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem
                every { cartRepository.save(any()) } returns existingCartItem.copy(quantity = existingQuantity + additionalQuantity)

                // when
                val result = cartService.addCartItem(userId, request)

                // then
                result.quantity shouldBe 5 // 2 + 3
                result.subtotal shouldBe 7500000L // 1500000 * 5

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
                verify(exactly = 1) { cartRepository.findByUserIdAndProductId(userId, productId) }
                verify(exactly = 1) { cartRepository.save(any()) }
                verify(exactly = 0) { cartRepository.generateId() }
            }

            it("수량 합산 후에도 최대 수량(100) 이하면 성공한다") {
                // given
                val userId = 1L
                val productId = 15L
                val existingQuantity = 50
                val additionalQuantity = 49
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 200, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = additionalQuantity)

                val now = LocalDateTime.now()
                val existingCartItem = CartItem(1L, userId, productId, existingQuantity, now)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem
                every { cartRepository.save(any()) } returns existingCartItem.copy(quantity = 99)

                // when
                val result = cartService.addCartItem(userId, request)

                // then
                result.quantity shouldBe 99

                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자로 상품 추가 시 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = 999L
                val request = AddCartItemRequest(productId = 1L, quantity = 1)
                every { userRepository.findById(invalidUserId) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.addCartItem(invalidUserId, request)
                }

                verify(exactly = 1) { userRepository.findById(invalidUserId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("수량이 0 이하면 InvalidQuantityException을 발생시킨다") {
                // given
                val userId = 1L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val request = AddCartItemRequest(productId = 1L, quantity = 0)

                every { userRepository.findById(userId) } returns user

                // when & then
                shouldThrow<InvalidQuantityException> {
                    cartService.addCartItem(userId, request)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("존재하지 않는 상품을 추가하면 ProductNotFoundException을 발생시킨다") {
                // given
                val userId = 1L
                val invalidProductId = 999L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val request = AddCartItemRequest(productId = invalidProductId, quantity = 1)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(invalidProductId) } returns null

                // when & then
                shouldThrow<ProductNotFoundException> {
                    cartService.addCartItem(userId, request)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(invalidProductId) }
            }

            it("품절된 상품을 추가하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 1L
                val productId = 15L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val outOfStockProduct = createProduct(productId, "품절 상품", 50000L, 0, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = 1)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns outOfStockProduct

                // when & then
                val exception = shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, request)
                }
                exception.message shouldContain "Insufficient stock"

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
            }

            it("최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = 1L
                val productId = 15L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 200, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = 101)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns null

                // when & then
                val exception = shouldThrow<ExceedMaxQuantityException> {
                    cartService.addCartItem(userId, request)
                }
                exception.message shouldContain "Exceed max quantity"

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
            }

            it("기존 수량과 합산하여 최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = 1L
                val productId = 15L
                val existingQuantity = 80
                val additionalQuantity = 25
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 200, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = additionalQuantity)

                val now = LocalDateTime.now()
                val existingCartItem = CartItem(1L, userId, productId, existingQuantity, now)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem

                // when & then
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.addCartItem(userId, request)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
                verify(exactly = 0) { cartRepository.save(any()) }
            }

            it("재고보다 많은 수량을 추가하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 1L
                val productId = 7L // 운동화 ABC, 재고 45개
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "운동화", 89000L, 45, ProductCategory.FASHION, 0)
                val request = AddCartItemRequest(productId = productId, quantity = 100) // 100 > 45

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns null

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, request)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
            }

            it("기존 수량과 합산하여 재고를 초과하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 1L
                val productId = 15L
                val existingQuantity = 5
                val additionalQuantity = 8
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 10, ProductCategory.ELECTRONICS, 0)
                val request = AddCartItemRequest(productId = productId, quantity = additionalQuantity)

                val now = LocalDateTime.now()
                val existingCartItem = CartItem(1L, userId, productId, existingQuantity, now)

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, request)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { productRepository.findById(productId) }
            }
        }
    }

    describe("CartService 단위 테스트 - updateCartItem") {
        context("정상 케이스") {
            it("장바구니 아이템의 수량을 변경한다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val newQuantity = 5
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val request = UpdateCartItemRequest(quantity = newQuantity)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { productRepository.findById(productId) } returns product
                every { cartRepository.save(any()) } returns cartItem.copy(quantity = newQuantity)

                // when
                val result = cartService.updateCartItem(userId, cartItemId, request)

                // then
                result.cartItemId shouldBe cartItemId
                result.quantity shouldBe newQuantity
                result.subtotal shouldBe 7500000L // 1500000 * 5

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { productRepository.findById(productId) }
                verify(exactly = 1) { cartRepository.save(any()) }
            }

            it("수량을 1로 변경할 수 있다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 10, now)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val request = UpdateCartItemRequest(quantity = 1)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { productRepository.findById(productId) } returns product
                every { cartRepository.save(any()) } returns cartItem.copy(quantity = 1)

                // when
                val result = cartService.updateCartItem(userId, cartItemId, request)

                // then
                result.quantity shouldBe 1

                verify(exactly = 1) { cartRepository.save(any()) }
            }

            it("수량을 최대값(100)으로 변경할 수 있다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 10, now)
                val product = createProduct(productId, "노트북", 1500000L, 150, ProductCategory.ELECTRONICS, 0)
                val request = UpdateCartItemRequest(quantity = 100)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { productRepository.findById(productId) } returns product
                every { cartRepository.save(any()) } returns cartItem.copy(quantity = 100)

                // when
                val result = cartService.updateCartItem(userId, cartItemId, request)

                // then
                result.quantity shouldBe 100

                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템을 수정하면 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = 1L
                val invalidCartItemId = 999L
                val request = UpdateCartItemRequest(quantity = 5)

                every { cartRepository.findById(invalidCartItemId) } returns null

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.updateCartItem(userId, invalidCartItemId, request)
                }

                verify(exactly = 1) { cartRepository.findById(invalidCartItemId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("다른 사용자의 장바구니 아이템을 수정하면 ForbiddenException을 발생시킨다") {
                // given
                val userId = 1L
                val otherUserId = 2L
                val cartItemId = 1L
                val now = LocalDateTime.now()

                val otherUserCartItem = CartItem(cartItemId, otherUserId, 15L, 2, now)
                val request = UpdateCartItemRequest(quantity = 5)

                every { cartRepository.findById(cartItemId) } returns otherUserCartItem

                // when & then
                val exception = shouldThrow<ForbiddenException> {
                    cartService.updateCartItem(userId, cartItemId, request)
                }
                exception.message shouldContain "다른 사용자의 장바구니 아이템입니다"

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("수량을 0으로 변경하면 아이템을 삭제하고 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)
                val request = UpdateCartItemRequest(quantity = 0)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { cartRepository.delete(cartItemId) } just Runs

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.updateCartItem(userId, cartItemId, request)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { cartRepository.delete(cartItemId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("수량을 음수로 변경하면 InvalidQuantityException을 발생시킨다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)
                val request = UpdateCartItemRequest(quantity = -1)

                every { cartRepository.findById(cartItemId) } returns cartItem

                // when & then
                shouldThrow<InvalidQuantityException> {
                    cartService.updateCartItem(userId, cartItemId, request)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("수량을 최대값(100)을 초과하여 변경하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)
                val request = UpdateCartItemRequest(quantity = 101)

                every { cartRepository.findById(cartItemId) } returns cartItem

                // when & then
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.updateCartItem(userId, cartItemId, request)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { productRepository.findById(any()) }
            }

            it("재고보다 많은 수량으로 변경하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)
                val product = createProduct(productId, "노트북", 1500000L, 5, ProductCategory.ELECTRONICS, 0)
                val request = UpdateCartItemRequest(quantity = 10)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { productRepository.findById(productId) } returns product

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.updateCartItem(userId, cartItemId, request)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { productRepository.findById(productId) }
            }

            it("상품이 삭제되었으면 ProductNotFoundException을 발생시킨다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)
                val request = UpdateCartItemRequest(quantity = 5)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { productRepository.findById(productId) } returns null

                // when & then
                shouldThrow<ProductNotFoundException> {
                    cartService.updateCartItem(userId, cartItemId, request)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { productRepository.findById(productId) }
            }
        }
    }

    describe("CartService 단위 테스트 - deleteCartItem") {
        context("정상 케이스") {
            it("장바구니 아이템을 삭제한다") {
                // given
                val userId = 1L
                val cartItemId = 1L
                val productId = 15L
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, productId, 2, now)

                every { cartRepository.findById(cartItemId) } returns cartItem
                every { cartRepository.delete(cartItemId) } just Runs

                // when
                cartService.deleteCartItem(userId, cartItemId)

                // then
                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { cartRepository.delete(cartItemId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템을 삭제하면 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = 1L
                val invalidCartItemId = 999L

                every { cartRepository.findById(invalidCartItemId) } returns null

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.deleteCartItem(userId, invalidCartItemId)
                }

                verify(exactly = 1) { cartRepository.findById(invalidCartItemId) }
                verify(exactly = 0) { cartRepository.delete(any()) }
            }

            it("다른 사용자의 장바구니 아이템을 삭제하면 ForbiddenException을 발생시킨다") {
                // given
                val userId = 1L
                val otherUserId = 2L
                val cartItemId = 1L
                val now = LocalDateTime.now()

                val otherUserCartItem = CartItem(cartItemId, otherUserId, 15L, 2, now)

                every { cartRepository.findById(cartItemId) } returns otherUserCartItem

                // when & then
                val exception = shouldThrow<ForbiddenException> {
                    cartService.deleteCartItem(userId, cartItemId)
                }
                exception.message shouldContain "다른 사용자의 장바구니 아이템입니다"

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { cartRepository.delete(any()) }
            }
        }
    }

    describe("CartService 단위 테스트 - clearCart") {
        context("정상 케이스") {
            it("장바구니를 전체 비운다") {
                // given
                val userId = 1L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                every { userRepository.findById(userId) } returns user
                every { cartRepository.deleteByUserId(userId) } just Runs

                // when
                cartService.clearCart(userId)

                // then
                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { cartRepository.deleteByUserId(userId) }
            }

            it("빈 장바구니를 비워도 정상 처리된다") {
                // given
                val userId = 1L
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                every { userRepository.findById(userId) } returns user
                every { cartRepository.deleteByUserId(userId) } just Runs

                // when
                cartService.clearCart(userId)

                // then
                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { cartRepository.deleteByUserId(userId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자의 장바구니를 비우면 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = 999L

                every { userRepository.findById(invalidUserId) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.clearCart(invalidUserId)
                }

                verify(exactly = 1) { userRepository.findById(invalidUserId) }
                verify(exactly = 0) { cartRepository.deleteByUserId(any()) }
            }
        }
    }
}) {
    companion object {
        fun createUser(
            id: Long,
            name: String,
            email: String,
            balance: Long
        ): User {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return User(
                id = id,
                balance = balance,
                createdAt = now,
                updatedAt = now
            )
        }

        fun createProduct(
            id: Long,
            name: String,
            price: Long,
            stock: Int,
            category: ProductCategory,
            salesCount: Int
        ): Product {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return Product(
                id = id,
                name = name,
                description = "$name 상세 설명",
                price = price,
                stock = stock,
                category = category,
                specifications = emptyMap(),
                salesCount = salesCount,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}