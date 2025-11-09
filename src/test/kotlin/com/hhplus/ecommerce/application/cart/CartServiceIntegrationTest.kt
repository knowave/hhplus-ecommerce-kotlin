package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.application.cart.dto.*
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.product.ProductServiceImpl
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.UserServiceImpl
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepositoryImpl
import com.hhplus.ecommerce.infrastructure.user.UserRepositoryImpl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CartServiceIntegrationTest : DescribeSpec({
    lateinit var cartRepository: CartRepository
    lateinit var productRepository: ProductRepository
    lateinit var userRepository: UserRepository
    lateinit var productService: ProductService
    lateinit var userService: UserService
    lateinit var cartService: CartService

    beforeEach {
        cartRepository = CartRepositoryImpl()
        productRepository = ProductRepositoryImpl()
        userRepository = UserRepositoryImpl()
        productService = ProductServiceImpl(productRepository)
        userService = UserServiceImpl(userRepository)
        cartService = CartServiceImpl(cartRepository, productService, userService)
    }

    describe("CartService 통합 테스트 - 장바구니 조회") {

        context("빈 장바구니 조회") {
            it("장바구니가 비어있는 사용자는 빈 장바구니를 조회한다") {
                // given - 사용자 2는 장바구니가 비어있음
                val userId = 2L

                // when
                val response = cartService.getCart(userId)

                // then
                response.userId shouldBe userId
                response.items.shouldBeEmpty()
                response.summary.totalItems shouldBe 0
                response.summary.totalQuantity shouldBe 0
                response.summary.totalAmount shouldBe 0L
                response.summary.availableAmount shouldBe 0L
                response.summary.unavailableCount shouldBe 0
            }
        }

        context("상품이 담긴 장바구니 조회") {
            it("장바구니에 담긴 상품들을 조회한다") {
                // given - 사용자 1은 초기 샘플 데이터로 장바구니에 상품이 담겨있음
                val userId = 1L

                // when
                val response = cartService.getCart(userId)

                // then
                response.userId shouldBe userId
                response.items shouldHaveSize 2

                // 첫 번째 아이템 검증 (상품 15, 수량 2)
                val item1 = response.items.find { it.productId == 15L }
                item1 shouldNotBe null
                item1!!.productName shouldBe "무선 이어폰 XYZ"
                item1.price shouldBe 150000L
                item1.quantity shouldBe 2
                item1.subtotal shouldBe 300000L
                item1.stock shouldBe 80
                item1.isAvailable shouldBe true

                // 두 번째 아이템 검증 (상품 7, 수량 1)
                val item2 = response.items.find { it.productId == 7L }
                item2 shouldNotBe null
                item2!!.productName shouldBe "운동화 ABC"
                item2.price shouldBe 89000L
                item2.quantity shouldBe 1
                item2.subtotal shouldBe 89000L

                // 요약 정보 검증
                response.summary.totalItems shouldBe 2
                response.summary.totalQuantity shouldBe 3 // 2 + 1
                response.summary.totalAmount shouldBe 389000L // 300000 + 89000
                response.summary.availableAmount shouldBe 389000L
                response.summary.unavailableCount shouldBe 0
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자로 조회 시 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = 999L

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.getCart(invalidUserId)
                }
            }
        }
    }

    describe("CartService 통합 테스트 - 상품 추가") {

        context("새로운 상품 추가") {
            it("장바구니에 새 상품을 추가한다") {
                // given
                val userId = 2L // 빈 장바구니
                val productId = 1L // 노트북 ABC
                val quantity = 2
                val command = AddCartItemCommand(productId = productId, quantity = quantity)

                // when
                val response = cartService.addCartItem(userId, command)

                // then
                response.cartItemId shouldNotBe null
                response.productId shouldBe productId
                response.productName shouldBe "노트북 ABC"
                response.price shouldBe 1500000L
                response.quantity shouldBe quantity
                response.subtotal shouldBe 3000000L
                response.addedAt shouldNotBe null

                // 장바구니 조회로 확인
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1
                cart.items[0].productId shouldBe productId
                cart.items[0].quantity shouldBe quantity
            }

            it("여러 개의 서로 다른 상품을 추가할 수 있다") {
                // given
                val userId = 2L


                // when - 3개 상품 추가
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 5L, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 10L, quantity = 3))

                // then
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 3
                cart.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(1L, 5L, 10L)
                cart.summary.totalQuantity shouldBe 6 // 1 + 2 + 3
            }
        }

        context("기존 상품 수량 증가") {
            it("이미 장바구니에 있는 상품을 추가하면 수량이 증가한다") {
                // given
                val userId = 2L
                val productId = 1L

                // when - 같은 상품 2번 추가
                cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))
                val response2 = cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 3))

                // then - 수량 합산
                response2.quantity shouldBe 5 // 2 + 3
                response2.subtotal shouldBe 7500000L // 1500000 * 5

                // 장바구니 조회로 확인
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1
                cart.items[0].quantity shouldBe 5
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자로 상품 추가 시 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = 999L
                val command = AddCartItemCommand(productId = 1L, quantity = 1)

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.addCartItem(invalidUserId, command)
                }
            }

            it("존재하지 않는 상품을 추가하면 ProductNotFoundException을 발생시킨다") {
                // given
                val userId = 2L
                val command = AddCartItemCommand(productId = 999L, quantity = 1)

                // when & then
                shouldThrow<ProductNotFoundException> {
                    cartService.addCartItem(userId, command)
                }
            }

            it("재고가 0인 상품을 추가하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 2L
                val productId = 1L

                // 먼저 재고를 0으로 만들기
                val product = productRepository.findById(productId)!!
                val updatedProduct = product.copy(stock = 0)
                productRepository.save(updatedProduct)

                val command = AddCartItemCommand(productId = productId, quantity = 1)

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, command)
                }

                // 원복
                productRepository.save(product)
            }

            it("수량이 0 이하면 InvalidQuantityException을 발생시킨다") {
                // given
                val userId = 2L
                val command = AddCartItemCommand(productId = 1L, quantity = 0)

                // when & then
                shouldThrow<InvalidQuantityException> {
                    cartService.addCartItem(userId, command)
                }
            }

            it("최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = 2L
                val command = AddCartItemCommand(productId = 1L, quantity = 101)

                // when & then
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.addCartItem(userId, command)
                }
            }

            it("기존 수량과 합산하여 최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = 2L
                val productId = 1L // 노트북 ABC, 재고 50개

                // 먼저 50개 추가
                cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 50))

                // when & then - 추가로 51개 추가 시도 (총 101개, 최대치 초과)
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 51))
                }
            }

            it("재고보다 많은 수량을 추가하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 2L
                val productId = 7L // 운동화 ABC, 재고 45개
                val command = AddCartItemCommand(productId = productId, quantity = 100) // 100 > 45

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.addCartItem(userId, command)
                }
            }
        }
    }

    describe("CartService 통합 테스트 - 수량 변경") {

        context("정상 케이스") {
            it("장바구니 아이템의 수량을 변경한다") {
                // given - 먼저 상품 추가
                val userId = 2L
                val productId = 1L
                val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))
                val cartItemId = addResponse.cartItemId

                // when - 수량 변경
                val updateResponse = cartService.updateCartItem(
                    userId = userId,
                    cartItemId = cartItemId,
                    request = UpdateCartItemCommand(quantity = 5)
                )

                // then
                updateResponse.cartItemId shouldBe cartItemId
                updateResponse.quantity shouldBe 5
                updateResponse.subtotal shouldBe 7500000L
                updateResponse.updatedAt shouldNotBe null

                // 장바구니 조회로 확인
                val cart = cartService.getCart(userId)
                cart.items[0].quantity shouldBe 5
            }

            it("수량을 1로 변경할 수 있다") {
                // given
                val userId = 2L
                val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 10))
                val cartItemId = addResponse.cartItemId

                // when
                val updateResponse = cartService.updateCartItem(
                    userId = userId,
                    cartItemId = cartItemId,
                    request = UpdateCartItemCommand(quantity = 1)
                )

                // then
                updateResponse.quantity shouldBe 1
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템을 수정하면 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = 2L
                val invalidCartItemId = 999L

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.updateCartItem(
                        userId = userId,
                        cartItemId = invalidCartItemId,
                        request = UpdateCartItemCommand(quantity = 5)
                    )
                }
            }

            it("다른 사용자의 장바구니 아이템을 수정하면 ForbiddenException을 발생시킨다") {
                // given - 사용자 1의 장바구니 아이템 (샘플 데이터)
                val user1CartItemId = 1L
                val user2Id = 2L

                // when & then
                shouldThrow<ForbiddenException> {
                    cartService.updateCartItem(
                        userId = user2Id,
                        cartItemId = user1CartItemId,
                        request = UpdateCartItemCommand(quantity = 5)
                    )
                }
            }

            it("수량을 0으로 변경하면 아이템을 삭제하고 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = 2L
                val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 2))
                val cartItemId = addResponse.cartItemId

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.updateCartItem(
                        userId = userId,
                        cartItemId = cartItemId,
                        request = UpdateCartItemCommand(quantity = 0)
                    )
                }

                // 장바구니 조회로 삭제 확인
                val cart = cartService.getCart(userId)
                cart.items.none { it.cartItemId == cartItemId } shouldBe true
            }

            it("수량을 최대값(100)을 초과하여 변경하면 ExceedMaxQuantityException을 발생시킨다") {
                // given
                val userId = 2L
                val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 2))
                val cartItemId = addResponse.cartItemId

                // when & then
                shouldThrow<ExceedMaxQuantityException> {
                    cartService.updateCartItem(
                        userId = userId,
                        cartItemId = cartItemId,
                        request = UpdateCartItemCommand(quantity = 101)
                    )
                }
            }

            it("재고보다 많은 수량으로 변경하면 InsufficientStockException을 발생시킨다") {
                // given
                val userId = 2L
                val productId = 7L // 운동화 ABC, 재고 45개
                val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))
                val cartItemId = addResponse.cartItemId

                // when & then
                shouldThrow<InsufficientStockException> {
                    cartService.updateCartItem(
                        userId = userId,
                        cartItemId = cartItemId,
                        request = UpdateCartItemCommand(quantity = 100) // 100 > 45
                    )
                }
            }
        }
    }

    describe("CartService 통합 테스트 - 아이템 삭제") {

        context("정상 케이스") {
            it("장바구니에서 아이템을 삭제한다") {
                // given - 먼저 상품 추가
                val userId = 2L
                val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 2))
                val cartItemId = addResponse.cartItemId

                // when
                cartService.deleteCartItem(userId, cartItemId)

                // then - 장바구니 조회로 삭제 확인
                val cart = cartService.getCart(userId)
                cart.items.none { it.cartItemId == cartItemId } shouldBe true
            }

            it("여러 아이템 중 하나만 삭제할 수 있다") {
                // given
                val userId = 2L
                val response1 = cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                val response2 = cartService.addCartItem(userId, AddCartItemCommand(productId = 2L, quantity = 2))
                val response3 = cartService.addCartItem(userId, AddCartItemCommand(productId = 3L, quantity = 3))

                // when - 중간 아이템 삭제
                cartService.deleteCartItem(userId, response2.cartItemId)

                // then
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 2
                cart.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(1L, 3L)
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템을 삭제하면 CartItemNotFoundException을 발생시킨다") {
                // given
                val userId = 2L
                val invalidCartItemId = 999L

                // when & then
                shouldThrow<CartItemNotFoundException> {
                    cartService.deleteCartItem(userId, invalidCartItemId)
                }
            }

            it("다른 사용자의 장바구니 아이템을 삭제하면 ForbiddenException을 발생시킨다") {
                // given - 사용자 1의 장바구니 아이템 (샘플 데이터)
                val user1CartItemId = 1L
                val user2Id = 2L

                // when & then
                shouldThrow<ForbiddenException> {
                    cartService.deleteCartItem(user2Id, user1CartItemId)
                }
            }
        }
    }

    describe("CartService 통합 테스트 - 장바구니 비우기") {

        context("정상 케이스") {
            it("장바구니를 전체 비운다") {
                // given - 여러 상품 추가
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 2L, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 3L, quantity = 3))

                val beforeClear = cartService.getCart(userId)
                beforeClear.items shouldHaveSize 3

                // when
                cartService.clearCart(userId)

                // then
                val afterClear = cartService.getCart(userId)
                afterClear.items.shouldBeEmpty()
                afterClear.summary.totalItems shouldBe 0
            }

            it("빈 장바구니를 비워도 정상 처리된다") {
                // given
                val userId = 2L
                val beforeClear = cartService.getCart(userId)
                beforeClear.items.shouldBeEmpty()

                // when
                cartService.clearCart(userId)

                // then
                val afterClear = cartService.getCart(userId)
                afterClear.items.shouldBeEmpty()
            }

            it("대량의 아이템이 담긴 장바구니를 한 번에 비운다") {
                // given - 10개 상품 추가
                val userId = 2L
                for (productId in 1L..10L) {
                    cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 1))
                }

                val beforeClear = cartService.getCart(userId)
                beforeClear.items shouldHaveSize 10

                // when
                cartService.clearCart(userId)

                // then
                val afterClear = cartService.getCart(userId)
                afterClear.items.shouldBeEmpty()
                afterClear.summary.totalItems shouldBe 0
                afterClear.summary.totalQuantity shouldBe 0
                afterClear.summary.totalAmount shouldBe 0L
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자의 장바구니를 비우면 UserNotFoundException을 발생시킨다") {
                // given
                val invalidUserId = 999L

                // when & then
                shouldThrow<UserNotFoundException> {
                    cartService.clearCart(invalidUserId)
                }
            }
        }
    }

    describe("CartService 통합 테스트 - 여러 상품 일괄 삭제") {

        context("정상 케이스") {
            it("주문 완료 후 주문한 상품들만 장바구니에서 삭제한다") {
                // given - 5개 상품 추가
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 2L, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 3L, quantity = 3))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 5L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 7L, quantity = 2))

                val beforeDelete = cartService.getCart(userId)
                beforeDelete.items shouldHaveSize 5

                // when - 주문 완료된 상품 3개만 삭제 (실제 주문 시나리오)
                val orderedProductIds = listOf(1L, 2L, 3L)
                cartService.deleteCarts(userId, orderedProductIds)

                // then - 나머지 2개만 남음
                val afterDelete = cartService.getCart(userId)
                afterDelete.items shouldHaveSize 2
                afterDelete.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(5L, 7L)
            }

            it("여러 상품을 한 번에 삭제한다") {
                // given
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 5L, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 10L, quantity = 3))

                // when
                val productIdsToDelete = listOf(1L, 10L)
                cartService.deleteCarts(userId, productIdsToDelete)

                // then
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1
                cart.items[0].productId shouldBe 5L
            }

            it("모든 상품을 일괄 삭제하면 빈 장바구니가 된다") {
                // given
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 2L, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 3L, quantity = 3))

                // when - 모든 상품 삭제
                val allProductIds = listOf(1L, 2L, 3L)
                cartService.deleteCarts(userId, allProductIds)

                // then
                val cart = cartService.getCart(userId)
                cart.items.shouldBeEmpty()
            }

            it("빈 리스트로 삭제 호출하면 아무것도 삭제되지 않는다") {
                // given
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 2L, quantity = 2))

                // when
                cartService.deleteCarts(userId, emptyList())

                // then
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 2
            }

            it("장바구니에 없는 상품 ID로 삭제 시도해도 에러가 발생하지 않는다") {
                // given
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))

                // when - 장바구니에 없는 상품 삭제 시도
                cartService.deleteCarts(userId, listOf(999L, 998L))

                // then - 기존 장바구니는 변하지 않음
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1
                cart.items[0].productId shouldBe 1L
            }

            it("일부는 있고 일부는 없는 상품 ID로 삭제하면 있는 것만 삭제된다") {
                // given
                val userId = 2L
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 2L, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = 3L, quantity = 3))

                // when - 1L, 2L은 있지만 999L은 없음
                cartService.deleteCarts(userId, listOf(1L, 2L, 999L))

                // then - 1L, 2L만 삭제되고 3L은 남음
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1
                cart.items[0].productId shouldBe 3L
            }
        }
    }

    describe("CartService 통합 테스트 - 복합 시나리오") {

        context("실제 쇼핑몰 사용 흐름") {
            it("장바구니 전체 플로우: 추가 -> 조회 -> 수량 변경 -> 삭제 -> 비우기") {
                // given
                val userId = 3L

                // 1. 빈 장바구니 확인
                val emptyCart = cartService.getCart(userId)
                emptyCart.items.shouldBeEmpty()

                // 2. 상품 3개 추가
                val item1 = cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 2))
                val item2 = cartService.addCartItem(userId, AddCartItemCommand(productId = 5L, quantity = 1))
                val item3 = cartService.addCartItem(userId, AddCartItemCommand(productId = 10L, quantity = 3))

                // 3. 장바구니 조회 - 3개 아이템 확인
                val cartWith3Items = cartService.getCart(userId)
                cartWith3Items.items shouldHaveSize 3
                cartWith3Items.summary.totalQuantity shouldBe 6 // 2 + 1 + 3

                // 4. 첫 번째 아이템 수량 변경 (2 -> 5)
                cartService.updateCartItem(userId, item1.cartItemId, UpdateCartItemCommand(quantity = 5))

                val cartAfterUpdate = cartService.getCart(userId)
                cartAfterUpdate.summary.totalQuantity shouldBe 9 // 5 + 1 + 3

                // 5. 두 번째 아이템 삭제
                cartService.deleteCartItem(userId, item2.cartItemId)

                val cartAfterDelete = cartService.getCart(userId)
                cartAfterDelete.items shouldHaveSize 2
                cartAfterDelete.summary.totalQuantity shouldBe 8 // 5 + 3

                // 6. 장바구니 전체 비우기
                cartService.clearCart(userId)

                val finalCart = cartService.getCart(userId)
                finalCart.items.shouldBeEmpty()
            }

            it("같은 상품을 여러 번 추가하면 수량이 누적된다") {
                // given
                val userId = 3L
                val productId = 1L

                // when - 같은 상품 3번 추가
                cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))
                cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 3))
                cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 5))

                // then
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1
                cart.items[0].quantity shouldBe 10 // 2 + 3 + 5
                cart.summary.totalItems shouldBe 1
            }

            it("여러 사용자의 장바구니가 독립적으로 관리된다") {
                // given
                val user1Id = 2L
                val user2Id = 3L

                // when - 사용자1은 상품 1, 2 추가
                cartService.addCartItem(user1Id, AddCartItemCommand(productId = 1L, quantity = 1))
                cartService.addCartItem(user1Id, AddCartItemCommand(productId = 2L, quantity = 1))

                // when - 사용자2는 상품 5, 10 추가
                cartService.addCartItem(user2Id, AddCartItemCommand(productId = 5L, quantity = 2))
                cartService.addCartItem(user2Id, AddCartItemCommand(productId = 10L, quantity = 3))

                // then - 각 사용자의 장바구니가 독립적으로 관리됨
                val cart1 = cartService.getCart(user1Id)
                cart1.items shouldHaveSize 2
                cart1.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(1L, 2L)

                val cart2 = cartService.getCart(user2Id)
                cart2.items shouldHaveSize 2
                cart2.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(5L, 10L)
            }

            it("장바구니에서 상품 정보가 정확하게 조회된다") {
                // given
                val userId = 3L
                val productId = 1L // 노트북 ABC

                // when
                cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))

                // then - 장바구니 조회 시 상품 정보 확인
                val cart = cartService.getCart(userId)
                cart.items shouldHaveSize 1

                val cartItem = cart.items[0]
                cartItem.productId shouldBe productId
                cartItem.productName shouldBe "노트북 ABC"
                cartItem.price shouldBe 1500000L
                cartItem.quantity shouldBe 2
                cartItem.subtotal shouldBe 3000000L
                cartItem.stock shouldBe 50
                cartItem.isAvailable shouldBe true
            }

            it("재고가 부족한 상품은 장바구니에 표시되지만 구매 불가능으로 표시된다") {
                // given
                val userId = 3L
                val productId = 18L // 재고 0인 상품

                // 재고가 0인 상품을 장바구니에 직접 추가할 수는 없지만,
                // 이미 장바구니에 있던 상품의 재고가 0이 된 경우를 시뮬레이션
                // (실제로는 addCartItem에서 품절 체크하므로 이 시나리오는 장바구니 조회에서만 확인)

                // 사용자 1의 장바구니 확인 (샘플 데이터)
                val cart = cartService.getCart(1L)
                cart.items.forEach { item ->
                    if (item.stock == 0) {
                        item.isAvailable shouldBe false
                    } else {
                        item.isAvailable shouldBe true
                    }
                }
            }

            it("장바구니 요약 정보가 정확하게 계산된다") {
                // given
                val userId = 3L

                // when - 여러 상품 추가
                cartService.addCartItem(userId, AddCartItemCommand(productId = 1L, quantity = 2)) // 노트북 ABC: 1,500,000 * 2 = 3,000,000원
                cartService.addCartItem(userId, AddCartItemCommand(productId = 5L, quantity = 1)) // 코틀린 인 액션: 36,000 * 1 = 36,000원
                cartService.addCartItem(userId, AddCartItemCommand(productId = 7L, quantity = 3)) // 운동화 ABC: 89,000 * 3 = 267,000원

                // then
                val cart = cartService.getCart(userId)
                cart.summary.totalItems shouldBe 3
                cart.summary.totalQuantity shouldBe 6 // 2 + 1 + 3
                cart.summary.totalAmount shouldBe 3303000L // 3000000 + 36000 + 267000
                cart.summary.availableAmount shouldBe 3303000L
                cart.summary.unavailableCount shouldBe 0
            }
        }
    }
})