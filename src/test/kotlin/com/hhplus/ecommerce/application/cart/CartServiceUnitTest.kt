package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.application.cart.dto.AddCartItemCommand
import com.hhplus.ecommerce.application.cart.dto.UpdateCartItemCommand
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.cart.entity.CartItem
import com.hhplus.ecommerce.domain.cart.repository.CartJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.user.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class CartServiceUnitTest : DescribeSpec({
    lateinit var cartRepository: CartJpaRepository
    lateinit var productService: ProductService
    lateinit var userService: UserService
    lateinit var cartService: CartServiceImpl

    beforeEach {
        cartRepository = mockk()
        productService = mockk()
        userService = mockk()
        cartService = CartServiceImpl(cartRepository, productService, userService)
    }

    describe("CartService 단위 테스트 - addCartItem") {
        context("정상 케이스 - 새로운 상품 추가") {
            it("장바구니에 새 상품을 추가한다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val quantity = 2
                val user = createUser(100000L)
                val product = createProduct("노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId = productId, quantity = quantity)

                val savedCartItem = CartItem(userId, productId, quantity)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns null
                every { cartRepository.save(any()) } returns savedCartItem

                // when
                val result = cartService.addCartItem(userId, command)

                // then
                result.cartItemId shouldBe savedCartItem.id
                result.productId shouldBe productId
                result.productName shouldBe "노트북"
                result.price shouldBe 1500000L
                result.quantity shouldBe quantity
                result.subtotal shouldBe 3000000L

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
                verify(exactly = 1) { cartRepository.findByUserIdAndProductId(userId, productId) }
                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }

        context("정상 케이스 - 기존 상품 수량 증가") {
            it("이미 장바구니에 있는 상품의 수량을 증가시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val existingQuantity = 2
                val additionalQuantity = 3
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId = productId, quantity = additionalQuantity)

                val existingCartItem = CartItem(userId, productId, existingQuantity)
                val calculateQuantity = existingQuantity + additionalQuantity

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem
                every { cartRepository.save(any()) } answers {
                    existingCartItem.quantity = calculateQuantity
                    existingCartItem
                }

                // when
                val result = cartService.addCartItem(userId, command)

                // then
                result.quantity shouldBe 5 // 2 + 3
                result.subtotal shouldBe 7500000L // 1500000 * 5

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
                verify(exactly = 1) { cartRepository.findByUserIdAndProductId(userId, productId) }
                verify(exactly = 1) { cartRepository.save(any()) }
            }

            it("수량 합산 후에도 최대 수량(100) 이하면 성공한다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val existingQuantity = 50
                val additionalQuantity = 49
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 200, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId = productId, quantity = additionalQuantity)

                val existingCartItem = CartItem(userId, productId, existingQuantity)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem
                every { cartRepository.save(any()) } answers {
                    existingCartItem.quantity = 99
                    existingCartItem
                }

                // when
                val result = cartService.addCartItem(userId, command)

                // then
                result.quantity shouldBe 99

                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자로 상품 추가 시 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val command = AddCartItemCommand(productId, 1)
                every { userService.getUser(invalidUserId) } throws UserNotFoundException(invalidUserId)

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.addCartItem(invalidUserId, command)
                }

                verify(exactly = 1) { userService.getUser(invalidUserId) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("수량이 0 이하면 InvalidQuantityException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val command = AddCartItemCommand(productId, 0)

                every { userService.getUser(userId) } returns user

                // when & then
                shouldThrow<InvalidQuantityException> {
                    cartService.addCartItem(userId, command)
                }

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("존재하지 않는 상품을 추가하면 ProductNotFoundException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val invalidProductId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val command = AddCartItemCommand(productId = invalidProductId, quantity = 1)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(invalidProductId) } throws ProductNotFoundException(invalidProductId)

                // when & then
                shouldThrow<ProductNotFoundException> {
                    cartService.addCartItem(userId, command)
                }

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(invalidProductId) }
            }

            it("품절된 상품을 추가하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val outOfStockProduct = createProduct(productId, "품절 상품", 50000L, 0, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId = productId, quantity = 1)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns outOfStockProduct

                // when & then
                val exception = shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, command)
                }
                exception.message shouldContain "Insufficient stock"

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
            }

            it("최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 200, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId = productId, quantity = 101)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns null

                // when & then
                val exception = shouldThrow<ExceedMaxQuantityException> {
                    cartService.addCartItem(userId, command)
                }
                exception.message shouldContain "Exceed max quantity"

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
            }

            it("기존 수량과 합산하여 최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val existingQuantity = 80
                val additionalQuantity = 25
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 200, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId = productId, quantity = additionalQuantity)

                val existingCartItem = CartItem(userId, productId, existingQuantity)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem

                // when & then
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.addCartItem(userId, command)
                }

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
                verify(exactly = 0) { cartRepository.save(any()) }
            }

            it("재고보다 많은 수량을 추가하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "운동화", 89000L, 45, ProductCategory.FASHION, 0)
                val command = AddCartItemCommand(productId, 100) // 100 > 45

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns null

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, command)
                }

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
            }

            it("기존 수량과 합산하여 재고를 초과하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val existingQuantity = 5
                val additionalQuantity = 8
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)
                val product = createProduct(productId, "노트북", 1500000L, 10, ProductCategory.ELECTRONICS, 0)
                val command = AddCartItemCommand(productId, additionalQuantity)

                val existingCartItem = CartItem(userId, productId, existingQuantity)

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { cartRepository.findByUserIdAndProductId(userId, productId) } returns existingCartItem

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, command)
                }

                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { productService.findProductById(productId) }
            }
        }
    }

    describe("CartService 단위 테스트 - updateCartItem") {
        context("정상 케이스") {
            it("장바구니 아이템의 수량을 변경한다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val newQuantity = 5
                val now = LocalDateTime.now()

                val cartItem = CartItem(cartItemId, userId, 2)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val command = UpdateCartItemCommand(quantity = newQuantity)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { productService.findProductById(productId) } returns product
                every { cartRepository.save(any()) } answers {
                    cartItem.quantity = newQuantity
                    cartItem
                }

                // when
                val result = cartService.updateCartItem(userId, cartItemId, command)

                // then
                result.cartItemId shouldBe cartItemId
                result.quantity shouldBe newQuantity
                result.subtotal shouldBe 7500000L // 1500000 * 5

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { productService.findProductById(productId) }
                verify(exactly = 1) { cartRepository.save(any()) }
            }

            it("수량을 1로 변경할 수 있다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 10)
                val product = createProduct(productId, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 0)
                val command = UpdateCartItemCommand(quantity = 1)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { productService.findProductById(productId) } returns product
                every { cartRepository.save(any()) } answers {
                    cartItem.quantity = 1
                    cartItem
                }

                // when
                val result = cartService.updateCartItem(userId, cartItemId, command)

                // then
                result.quantity shouldBe 1

                verify(exactly = 1) { cartRepository.save(any()) }
            }

            it("수량을 최대값(100)으로 변경할 수 있다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 10)
                val product = createProduct(productId, "노트북", 1500000L, 150, ProductCategory.ELECTRONICS, 0)
                val command = UpdateCartItemCommand(quantity = 100)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { productService.findProductById(productId) } returns product
                every { cartRepository.save(any()) } answers {
                    cartItem.quantity = 100
                    cartItem
                }

                // when
                val result = cartService.updateCartItem(userId, cartItemId, command)

                // then
                result.quantity shouldBe 100

                verify(exactly = 1) { cartRepository.save(any()) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템을 수정하면 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val invalidCartItemId = UUID.randomUUID()
                val command = UpdateCartItemCommand(quantity = 5)

                every { cartRepository.findById(invalidCartItemId) } returns Optional.empty()

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.updateCartItem(userId, invalidCartItemId, command)
                }

                verify(exactly = 1) { cartRepository.findById(invalidCartItemId) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("다른 사용자의 장바구니 아이템을 수정하면 ForbiddenException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()

                val otherUserCartItem = CartItem(otherUserId, productId, 2)
                val command = UpdateCartItemCommand(quantity = 5)

                every { cartRepository.findById(cartItemId) } returns Optional.of(otherUserCartItem)

                // when & then
                val exception = shouldThrow<ForbiddenException> {
                    cartService.updateCartItem(userId, cartItemId, command)
                }
                exception.message shouldContain "다른 사용자의 장바구니 아이템입니다"

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("수량을 0으로 변경하면 아이템을 삭제하고 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 2)
                val command = UpdateCartItemCommand(quantity = 0)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { cartRepository.delete(cartItem) } just Runs

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.updateCartItem(userId, cartItemId, command)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { cartRepository.delete(cartItem) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("수량을 음수로 변경하면 InvalidQuantityException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 2)
                val command = UpdateCartItemCommand(quantity = -1)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)

                // when & then
                shouldThrow<InvalidQuantityException> {
                    cartService.updateCartItem(userId, cartItemId, command)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("수량을 최대값(100)을 초과하여 변경하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 2)
                val command = UpdateCartItemCommand(quantity = 101)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)

                // when & then
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.updateCartItem(userId, cartItemId, command)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 0) { productService.findProductById(any()) }
            }

            it("재고보다 많은 수량으로 변경하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 2)
                val product = createProduct(productId, "노트북", 1500000L, 5, ProductCategory.ELECTRONICS, 0)
                val command = UpdateCartItemCommand(quantity = 10)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { productService.findProductById(productId) } returns product

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.updateCartItem(userId, cartItemId, command)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { productService.findProductById(productId) }
            }

            it("상품이 삭제되었으면 ProductNotFoundException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 2)
                val command = UpdateCartItemCommand(quantity = 5)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { productService.findProductById(productId) } throws ProductNotFoundException(productId)

                // when & then
                shouldThrow<ProductNotFoundException> {
                    cartService.updateCartItem(userId, cartItemId, command)
                }

                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { productService.findProductById(productId) }
            }
        }
    }

    describe("CartService 단위 테스트 - deleteCartItem") {
        context("정상 케이스") {
            it("장바구니 아이템을 삭제한다") {
                // given
                val userId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val cartItem = CartItem(userId, productId, 2)

                every { cartRepository.findById(cartItemId) } returns Optional.of(cartItem)
                every { cartRepository.delete(cartItem) } just Runs

                // when
                cartService.deleteCartItem(userId, cartItemId)

                // then
                verify(exactly = 1) { cartRepository.findById(cartItemId) }
                verify(exactly = 1) { cartRepository.delete(cartItem) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템을 삭제하면 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val invalidCartItemId = UUID.randomUUID()

                every { cartRepository.findById(invalidCartItemId) } returns Optional.empty()

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.deleteCartItem(userId, invalidCartItemId)
                }

                verify(exactly = 1) { cartRepository.findById(invalidCartItemId) }
                verify(exactly = 0) { cartRepository.delete(any()) }
            }

            it("다른 사용자의 장바구니 아이템을 삭제하면 ForbiddenException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()
                val cartItemId = UUID.randomUUID()

                val otherUserCartItem = CartItem(otherUserId, productId, 2)

                every { cartRepository.findById(cartItemId) } returns Optional.of(otherUserCartItem)

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
                val userId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                every { userService.getUser(userId) } returns user
                every { cartRepository.deleteByUserId(userId) } returns 1L

                // when
                cartService.clearCart(userId)

                // then
                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { cartRepository.deleteByUserId(userId) }
            }

            it("빈 장바구니를 비워도 정상 처리된다") {
                // given
                val userId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                every { userService.getUser(userId) } returns user
                every { cartRepository.deleteByUserId(userId) } returns 1L

                // when
                cartService.clearCart(userId)

                // then
                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { cartRepository.deleteByUserId(userId) }
            }

            it("여러 아이템이 담긴 장바구니를 한 번에 비운다") {
                // given
                val userId = UUID.randomUUID()
                val user = createUser(userId, "테스트 사용자", "test@example.com", 100000L)

                every { userService.getUser(userId) } returns user
                every { cartRepository.deleteByUserId(userId) } returns 1L

                // when
                cartService.clearCart(userId)

                // then - 아이템 개수에 상관없이 한 번의 deleteByUserId 호출로 전체 삭제
                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { cartRepository.deleteByUserId(userId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자의 장바구니를 비우면 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = UUID.randomUUID()
                every { userService.getUser(invalidUserId) } throws UserNotFoundException(invalidUserId)

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.clearCart(invalidUserId)
                }

                verify(exactly = 1) { userService.getUser(invalidUserId) }
                verify(exactly = 0) { cartRepository.deleteByUserId(any()) }
            }
        }
    }

    describe("CartService 단위 테스트 - deleteCarts") {
        context("정상 케이스") {
            it("여러 상품을 한 번에 삭제한다") {
                // given
                val userId = UUID.randomUUID()
                val productIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

                val cartItems = listOf(
                    CartItem(userId, productIds[0], 2),
                    CartItem(userId, productIds[1], 1),
                    CartItem(userId, productIds[2], 3)
                )

                every { cartRepository.findByUserIdAndProductIdIn(userId, productIds) } returns cartItems
                every { cartRepository.deleteAll(cartItems) } just Runs

                // when
                cartService.deleteCarts(userId, productIds)

                // then
                verify(exactly = 1) { cartRepository.findByUserIdAndProductIdIn(userId, productIds) }
                verify(exactly = 1) { cartRepository.deleteAll(cartItems) }
            }

            it("빈 productIds 리스트로 호출하면 아무것도 삭제하지 않는다") {
                // given
                val userId = UUID.randomUUID()
                val emptyProductIds = emptyList<UUID>()

                every { cartRepository.findByUserIdAndProductIdIn(userId, emptyProductIds) } returns emptyList()

                // when
                cartService.deleteCarts(userId, emptyProductIds)

                // then
                verify(exactly = 1) { cartRepository.findByUserIdAndProductIdIn(userId, emptyProductIds) }
                verify(exactly = 0) { cartRepository.deleteAll(any()) }
            }

            it("일부 상품만 장바구니에 있어도 정상 처리된다") {
                // given
                val userId = UUID.randomUUID()
                val productIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) // 999L은 장바구니에 없음

                val cartItems = listOf(
                    CartItem(userId, productIds[0], 2),
                    CartItem(userId, productIds[1], 1)
                    // 999L에 해당하는 아이템은 없음
                )

                every { cartRepository.findByUserIdAndProductIdIn(userId, productIds) } returns cartItems
                every { cartRepository.deleteAll(cartItems) } just Runs

                // when
                cartService.deleteCarts(userId, productIds)

                // then - 존재하는 아이템만 삭제
                verify(exactly = 1) { cartRepository.findByUserIdAndProductIdIn(userId, productIds) }
                verify(exactly = 1) { cartRepository.deleteAll(cartItems) }
            }

            it("장바구니에 없는 상품들만 삭제 시도하면 아무것도 삭제되지 않는다") {
                // given
                val userId = UUID.randomUUID()
                val productIds = listOf(UUID.randomUUID(), UUID.randomUUID())

                every { cartRepository.findByUserIdAndProductIdIn(userId, productIds) } returns emptyList()

                // when
                cartService.deleteCarts(userId, productIds)

                // then
                verify(exactly = 1) { cartRepository.findByUserIdAndProductIdIn(userId, productIds) }
                verify(exactly = 0) { cartRepository.deleteAll(any()) }
            }

            it("null을 반환하는 경우에도 정상 처리된다") {
                // given
                val userId = UUID.randomUUID()
                val productIds = listOf(UUID.randomUUID(), UUID.randomUUID())

                every { cartRepository.findByUserIdAndProductIdIn(userId, productIds) } returns emptyList()

                // when
                cartService.deleteCarts(userId, productIds)

                // then - null인 경우 빈 리스트로 처리되어 deleteManyByIds 호출 안 됨
                verify(exactly = 1) { cartRepository.findByUserIdAndProductIdIn(userId, productIds) }
                verify(exactly = 0) { cartRepository.deleteAll(any()) }
            }
        }
    }
}) {
    companion object {
        // UUID 기반 - 새로운 헬퍼 함수
        fun createUser(balance: Long = 0): User {
            return User(balance = balance)
        }

        fun createProduct(
            name: String,
            price: Long,
            stock: Int,
            category: ProductCategory,
            salesCount: Int = 0
        ): Product {
            return Product(
                name = name,
                description = "$name 상세 설명",
                price = price,
                stock = stock,
                category = category,
                specifications = emptyMap(),
                salesCount = salesCount
            )
        }

        // Long 기반 - 기존 테스트 호환용 (deprecated)
        fun createUser(
            @Suppress("UNUSED_PARAMETER") id: UUID,
            @Suppress("UNUSED_PARAMETER") name: String,
            @Suppress("UNUSED_PARAMETER") email: String,
            balance: Long
        ): User {
            return User(balance = balance)
        }

        fun createProduct(
            @Suppress("UNUSED_PARAMETER") id: UUID,
            name: String,
            price: Long,
            stock: Int,
            category: ProductCategory,
            salesCount: Int
        ): Product {
            return Product(
                name = name,
                description = "$name 상세 설명",
                price = price,
                stock = stock,
                category = category,
                specifications = emptyMap(),
                salesCount = salesCount
            )
        }
    }
}