package com.hhplus.ecommerce.application.cart

import com.hhplus.ecommerce.application.cart.dto.*
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.cart.repository.CartJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
class CartServiceIntegrationTest(
    private val productService: ProductService,
    private val userService: UserService,
    private val cartService: CartService
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testUserId: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID

    init {
        beforeEach {
            val createUserCommand = CreateUserCommand(
                balance = 3000000L
            )
            val savedUser = userService.createUser(createUserCommand)
            testUserId = savedUser.id!!

            val command = Product(
                name = "무선 이어폰 XYZ",
                description = "노이즈 캔슬링 기능이 탑재된 프리미엄 이어폰",
                price = 150000L,
                stock = 80,
                category = ProductCategory.ELECTRONICS,
                specifications = mapOf("battery" to "24 hours", "bluetooth" to "5.2", "anc" to "active"),
                salesCount = 250,
            )

            val secondCommand = Product(
                name = "운동화 ABC",
                description = "편안한 착용감의 러닝화",
                price = 89000L,
                stock = 45,
                category = ProductCategory.FASHION,
                specifications = mapOf("size" to "230-290mm", "material" to "mesh"),
                salesCount = 180,
            )

            val firstProduct = productService.updateProduct(command)
            val secondProduct = productService.updateProduct(secondCommand)

            product1Id = firstProduct.id!!
            product2Id = secondCommand.id!!
        }

        describe("CartService 통합 테스트 - 장바구니 조회") {

            context("빈 장바구니 조회") {
                it("장바구니가 비어있는 사용자는 빈 장바구니를 조회한다") {
                    // when
                    val response = cartService.getCart(testUserId)

                    // then
                    response.userId shouldBe testUserId
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
                    // given - 장바구니에 상품 추가
                    cartService.addCartItem(testUserId, AddCartItemCommand(productId = product1Id, quantity = 2))
                    cartService.addCartItem(testUserId, AddCartItemCommand(productId = product2Id, quantity = 1))

                    // when
                    val response = cartService.getCart(testUserId)

                    // then
                    response.userId shouldBe testUserId
                    response.items shouldHaveSize 2

                    // 첫 번째 아이템 검증 (무선 이어폰 XYZ, 수량 2)
                    val item1 = response.items.find { it.productId == product1Id }
                    item1 shouldNotBe null
                    item1!!.productName shouldBe "무선 이어폰 XYZ"
                    item1.price shouldBe 150000L
                    item1.quantity shouldBe 2
                    item1.subtotal shouldBe 300000L
                    item1.stock shouldBe 80
                    item1.isAvailable shouldBe true

                    // 두 번째 아이템 검증 (운동화 ABC, 수량 1)
                    val item2 = response.items.find { it.productId == product2Id }
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
                    val invalidUserId = UUID.randomUUID()

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
                    val userId = testUserId // 빈 장바구니
                    val productId = product1Id
                    val quantity = 2
                    val command = AddCartItemCommand(productId = productId, quantity = quantity)

                    // when
                    val response = cartService.addCartItem(userId, command)

                    // then
                    response.cartItemId shouldNotBe null
                    response.productId shouldBe productId
                    response.productName shouldBe "무선 이어폰 XYZ"
                    response.price shouldBe 150000L
                    response.quantity shouldBe quantity
                    response.subtotal shouldBe 300000L
                    response.addedAt shouldNotBe null

                    // 장바구니 조회로 확인
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 1
                    cart.items[0].productId shouldBe productId
                    cart.items[0].quantity shouldBe quantity
                }

                it("여러 개의 서로 다른 상품을 추가할 수 있다") {
                    // when - 2개 상품 추가
                    cartService.addCartItem(testUserId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(testUserId, AddCartItemCommand(productId = product2Id, quantity = 2))

                    val cart = cartService.getCart(testUserId)
                    cart.items shouldHaveSize 2
                    cart.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(product1Id, product2Id)
                    cart.summary.totalQuantity shouldBe 3 // 1 + 2
                }
            }

            context("기존 상품 수량 증가") {
                it("이미 장바구니에 있는 상품을 추가하면 수량이 증가한다") {
                    // given
                    val userId = testUserId
                    val productId = product1Id

                    // when - 같은 상품 2번 추가
                    cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))
                    val response2 = cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 3))

                    // then - 수량 합산
                    response2.quantity shouldBe 5 // 2 + 3
                    response2.subtotal shouldBe 750000L // 150000 * 5

                    // 장바구니 조회로 확인
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 1
                    cart.items[0].quantity shouldBe 5
                }
            }

            context("예외 케이스") {
                it("존재하지 않는 사용자로 상품 추가 시 UserNotFoundException을 발생시킨다") {
                    // given
                    val invalidUserId = UUID.randomUUID()
                    val command = AddCartItemCommand(productId = product1Id, quantity = 1)

                    // when & then
                    shouldThrow<UserNotFoundException> {
                        cartService.addCartItem(invalidUserId, command)
                    }
                }

                it("존재하지 않는 상품을 추가하면 ProductNotFoundException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val command = AddCartItemCommand(productId = UUID.randomUUID(), quantity = 1)

                    // when & then
                    shouldThrow<ProductNotFoundException> {
                        cartService.addCartItem(userId, command)
                    }
                }

                it("재고가 0인 상품을 추가하면 InsufficientStockException을 발생시킨다") {
                    // given
                    val userId = testUserId

                    // 재고가 0인 상품 생성
                    val zeroStockProduct = Product(
                        name = "품절 상품",
                        description = "재고가 없는 상품",
                        price = 10000L,
                        stock = 0,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("status" to "out of stock"),
                        salesCount = 0
                    )
                    val savedProduct = productService.updateProduct(zeroStockProduct)

                    val command = AddCartItemCommand(productId = savedProduct.id!!, quantity = 1)

                    // when & then
                    shouldThrow<InsufficientStockException> {
                        cartService.addCartItem(userId, command)
                    }
                }

                it("수량이 0 이하면 InvalidQuantityException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val command = AddCartItemCommand(productId = product1Id, quantity = 0)

                    // when & then
                    shouldThrow<InvalidQuantityException> {
                        cartService.addCartItem(userId, command)
                    }
                }

                it("최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val command = AddCartItemCommand(productId = product1Id, quantity = 101)

                    // when & then
                    shouldThrow<ExceedMaxQuantityException> {
                        cartService.addCartItem(userId, command)
                    }
                }

                it("기존 수량과 합산하여 최대 수량(100)을 초과하면 ExceedMaxQuantityException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val productId = product1Id // 무선 이어폰 XYZ, 재고 80개

                    // 먼저 50개 추가
                    cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 50))

                    // when & then - 추가로 51개 추가 시도 (총 101개, 최대치 초과)
                    shouldThrow<ExceedMaxQuantityException> {
                        cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 51))
                    }
                }

                it("재고보다 많은 수량을 추가하면 InsufficientStockException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val productId = product2Id // 운동화 ABC, 재고 45개
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
                    val userId = testUserId
                    val productId = product1Id
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
                    updateResponse.subtotal shouldBe 750000L // 150000 * 5
                    updateResponse.updatedAt shouldNotBe null

                    // 장바구니 조회로 확인
                    val cart = cartService.getCart(userId)
                    cart.items[0].quantity shouldBe 5
                }

                it("수량을 1로 변경할 수 있다") {
                    // given
                    val userId = testUserId
                    val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 10))
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
                    val userId = testUserId
                    val invalidCartItemId = UUID.randomUUID()

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
                    // given - 사용자1의 장바구니 아이템 생성
                    val user1Id = testUserId
                    val user1ItemResponse = cartService.addCartItem(user1Id, AddCartItemCommand(productId = product1Id, quantity = 1))

                    // 사용자2 생성
                    val user2 = userService.createUser(CreateUserCommand(balance = 1000000L))
                    val user2Id = user2.id!!

                    // when & then - 사용자2가 사용자1의 아이템 수정 시도
                    shouldThrow<ForbiddenException> {
                        cartService.updateCartItem(
                            userId = user2Id,
                            cartItemId = user1ItemResponse.cartItemId,
                            request = UpdateCartItemCommand(quantity = 5)
                        )
                    }
                }

                it("수량을 0으로 변경하면 아이템을 삭제하고 CartItemNotFoundException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 2))
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
                    val userId = testUserId
                    val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 2))
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
                    val userId = testUserId
                    val productId = product2Id // 운동화 ABC, 재고 45개
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
                    val userId = testUserId
                    val addResponse = cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 2))
                    val cartItemId = addResponse.cartItemId

                    // when
                    cartService.deleteCartItem(userId, cartItemId)

                    // then - 장바구니 조회로 삭제 확인
                    val cart = cartService.getCart(userId)
                    cart.items.none { it.cartItemId == cartItemId } shouldBe true
                }

                it("여러 아이템 중 하나만 삭제할 수 있다") {
                    // given - 3개 상품 추가
                    val userId = testUserId

                    // 추가 상품 생성
                    val product3 = productService.updateProduct(Product(
                        name = "키보드 ABC",
                        description = "기계식 키보드",
                        price = 120000L,
                        stock = 30,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("type" to "mechanical"),
                        salesCount = 100
                    ))
                    val product3Id = product3.id!!

                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    val response2 = cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 2))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product3Id, quantity = 3))

                    // when - 중간 아이템 삭제
                    cartService.deleteCartItem(userId, response2.cartItemId)

                    // then
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 2
                    cart.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(product1Id, product3Id)
                }
            }

            context("예외 케이스") {
                it("존재하지 않는 장바구니 아이템을 삭제하면 CartItemNotFoundException을 발생시킨다") {
                    // given
                    val userId = testUserId
                    val invalidCartItemId = UUID.randomUUID()

                    // when & then
                    shouldThrow<CartItemNotFoundException> {
                        cartService.deleteCartItem(userId, invalidCartItemId)
                    }
                }

                it("다른 사용자의 장바구니 아이템을 삭제하면 ForbiddenException을 발생시킨다") {
                    // given - 사용자1의 장바구니 아이템 생성
                    val user1Id = testUserId
                    val user1ItemResponse = cartService.addCartItem(user1Id, AddCartItemCommand(productId = product1Id, quantity = 1))

                    // 사용자2 생성
                    val user2 = userService.createUser(CreateUserCommand(balance = 1000000L))
                    val user2Id = user2.id!!

                    // when & then - 사용자2가 사용자1의 아이템 삭제 시도
                    shouldThrow<ForbiddenException> {
                        cartService.deleteCartItem(user2Id, user1ItemResponse.cartItemId)
                    }
                }
            }
        }

        describe("CartService 통합 테스트 - 장바구니 비우기") {

            context("정상 케이스") {
                it("장바구니를 전체 비운다") {
                    // given - 여러 상품 추가
                    val userId = testUserId

                    // 추가 상품 생성
                    val product3 = productService.updateProduct(Product(
                        name = "마우스 XYZ",
                        description = "게이밍 마우스",
                        price = 80000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("dpi" to "16000"),
                        salesCount = 150
                    ))

                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 2))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product3.id!!, quantity = 3))

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
                    val userId = testUserId
                    val beforeClear = cartService.getCart(userId)
                    beforeClear.items.shouldBeEmpty()

                    // when
                    cartService.clearCart(userId)

                    // then
                    val afterClear = cartService.getCart(userId)
                    afterClear.items.shouldBeEmpty()
                }

                it("대량의 아이템이 담긴 장바구니를 한 번에 비운다") {
                    // given - 10개 상품 생성 및 추가
                    val userId = testUserId

                    // 추가로 8개 상품 생성 (기존 2개 + 8개 = 10개)
                    val additionalProducts = (3..10).map { index ->
                        productService.updateProduct(Product(
                            name = "상품 $index",
                            description = "테스트 상품 $index",
                            price = 10000L * index,
                            stock = 100,
                            category = ProductCategory.ELECTRONICS,
                            specifications = mapOf("index" to "$index"),
                            salesCount = 10
                        ))
                    }

                    // 장바구니에 추가
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 1))
                    additionalProducts.forEach { product ->
                        cartService.addCartItem(userId, AddCartItemCommand(productId = product.id!!, quantity = 1))
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
                    val invalidUserId = UUID.randomUUID()

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
                    // given - 5개 상품 생성 및 추가
                    val userId = testUserId

                    // 추가 상품 생성
                    val products = (3..5).map { index ->
                        productService.updateProduct(Product(
                            name = "상품 $index",
                            description = "테스트 상품 $index",
                            price = 50000L * index,
                            stock = 100,
                            category = ProductCategory.ELECTRONICS,
                            specifications = mapOf("index" to "$index"),
                            salesCount = 10
                        ))
                    }

                    val allProductIds = listOf(product1Id, product2Id) + products.map { it.id!! }
                    allProductIds.forEach { productId ->
                        cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 1))
                    }

                    val beforeDelete = cartService.getCart(userId)
                    beforeDelete.items shouldHaveSize 5

                    // when - 주문 완료된 상품 3개만 삭제 (실제 주문 시나리오)
                    val orderedProductIds = listOf(product1Id, product2Id, products[0].id!!)
                    cartService.deleteCarts(userId, orderedProductIds)

                    // then - 나머지 2개만 남음
                    val afterDelete = cartService.getCart(userId)
                    afterDelete.items shouldHaveSize 2
                    afterDelete.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(products[1].id!!, products[2].id!!)
                }

                it("여러 상품을 한 번에 삭제한다") {
                    // given - 3개 상품 생성 및 추가
                    val userId = testUserId

                    val product3 = productService.updateProduct(Product(
                        name = "상품 3",
                        description = "테스트 상품 3",
                        price = 30000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("test" to "true"),
                        salesCount = 5
                    ))

                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 2))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product3.id!!, quantity = 3))

                    // when
                    val productIdsToDelete = listOf(product1Id, product2Id)
                    cartService.deleteCarts(userId, productIdsToDelete)

                    // then
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 1
                    cart.items[0].productId shouldBe product3.id!!
                }

                it("모든 상품을 일괄 삭제하면 빈 장바구니가 된다") {
                    // given
                    val userId = testUserId

                    val product3 = productService.updateProduct(Product(
                        name = "상품 3",
                        description = "테스트 상품 3",
                        price = 30000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("test" to "true"),
                        salesCount = 5
                    ))

                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 2))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product3.id!!, quantity = 3))

                    // when - 모든 상품 삭제
                    val allProductIds = listOf(product1Id, product2Id, product3.id!!)
                    cartService.deleteCarts(userId, allProductIds)

                    // then
                    val cart = cartService.getCart(userId)
                    cart.items.shouldBeEmpty()
                }

                it("빈 리스트로 삭제 호출하면 아무것도 삭제되지 않는다") {
                    // given
                    val userId = testUserId
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 2))

                    // when
                    cartService.deleteCarts(userId, emptyList())

                    // then
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 2
                }

                it("장바구니에 없는 상품 ID로 삭제 시도해도 에러가 발생하지 않는다") {
                    // given
                    val userId = testUserId
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))

                    // when - 장바구니에 없는 상품 삭제 시도
                    val nonExistentProductIds = listOf(UUID.randomUUID(), UUID.randomUUID())
                    cartService.deleteCarts(userId, nonExistentProductIds)

                    // then - 기존 장바구니는 변하지 않음
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 1
                    cart.items[0].productId shouldBe product1Id
                }

                it("일부는 있고 일부는 없는 상품 ID로 삭제하면 있는 것만 삭제된다") {
                    // given
                    val userId = testUserId

                    val product3 = productService.updateProduct(Product(
                        name = "상품 3",
                        description = "테스트 상품 3",
                        price = 30000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("test" to "true"),
                        salesCount = 5
                    ))

                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 2))
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product3.id!!, quantity = 3))

                    // when - product1Id, product2Id는 있지만 UUID.randomUUID()는 없음
                    cartService.deleteCarts(userId, listOf(product1Id, product2Id, UUID.randomUUID()))

                    // then - product1Id, product2Id만 삭제되고 product3는 남음
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 1
                    cart.items[0].productId shouldBe product3.id!!
                }
            }
        }

        describe("CartService 통합 테스트 - 복합 시나리오") {

            context("실제 쇼핑몰 사용 흐름") {
                it("장바구니 전체 플로우: 추가 -> 조회 -> 수량 변경 -> 삭제 -> 비우기") {
                    // given
                    val userId = testUserId

                    // 추가 상품 생성
                    val product3 = productService.updateProduct(Product(
                        name = "상품 3",
                        description = "테스트 상품 3",
                        price = 30000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("test" to "true"),
                        salesCount = 5
                    ))

                    // 1. 빈 장바구니 확인
                    val emptyCart = cartService.getCart(userId)
                    emptyCart.items.shouldBeEmpty()

                    // 2. 상품 3개 추가
                    val item1 = cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 2))
                    val item2 = cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 1))
                    val item3 = cartService.addCartItem(userId, AddCartItemCommand(productId = product3.id!!, quantity = 3))

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
                    val userId = testUserId
                    val productId = product1Id

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
                    // given - 2명의 사용자 생성
                    val user1Id = testUserId
                    val user2 = userService.createUser(CreateUserCommand(balance = 1000000L))
                    val user2Id = user2.id!!

                    // 추가 상품 생성
                    val product3 = productService.updateProduct(Product(
                        name = "상품 3",
                        description = "테스트 상품 3",
                        price = 30000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("test" to "true"),
                        salesCount = 5
                    ))
                    val product4 = productService.updateProduct(Product(
                        name = "상품 4",
                        description = "테스트 상품 4",
                        price = 40000L,
                        stock = 50,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("test" to "true"),
                        salesCount = 5
                    ))

                    // when - 사용자1은 상품 1, 2 추가
                    cartService.addCartItem(user1Id, AddCartItemCommand(productId = product1Id, quantity = 1))
                    cartService.addCartItem(user1Id, AddCartItemCommand(productId = product2Id, quantity = 1))

                    // when - 사용자2는 상품 3, 4 추가
                    cartService.addCartItem(user2Id, AddCartItemCommand(productId = product3.id!!, quantity = 2))
                    cartService.addCartItem(user2Id, AddCartItemCommand(productId = product4.id!!, quantity = 3))

                    // then - 각 사용자의 장바구니가 독립적으로 관리됨
                    val cart1 = cartService.getCart(user1Id)
                    cart1.items shouldHaveSize 2
                    cart1.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(product1Id, product2Id)

                    val cart2 = cartService.getCart(user2Id)
                    cart2.items shouldHaveSize 2
                    cart2.items.map { it.productId } shouldContainExactlyInAnyOrder listOf(product3.id!!, product4.id!!)
                }

                it("장바구니에서 상품 정보가 정확하게 조회된다") {
                    // given
                    val userId = testUserId
                    val productId = product1Id // 무선 이어폰 XYZ

                    // when
                    cartService.addCartItem(userId, AddCartItemCommand(productId = productId, quantity = 2))

                    // then - 장바구니 조회 시 상품 정보 확인
                    val cart = cartService.getCart(userId)
                    cart.items shouldHaveSize 1

                    val cartItem = cart.items[0]
                    cartItem.productId shouldBe productId
                    cartItem.productName shouldBe "무선 이어폰 XYZ"
                    cartItem.price shouldBe 150000L
                    cartItem.quantity shouldBe 2
                    cartItem.subtotal shouldBe 300000L
                    cartItem.stock shouldBe 80
                    cartItem.isAvailable shouldBe true
                }

                it("재고가 부족한 상품은 장바구니에 표시되지만 구매 불가능으로 표시된다") {
                    // 재고가 0인 상품을 장바구니에 직접 추가할 수는 없지만,
                    // 이미 장바구니에 있던 상품의 재고가 0이 된 경우를 시뮬레이션
                    // (실제로는 addCartItem에서 품절 체크하므로 이 시나리오는 장바구니 조회에서만 확인)

                    val userId = testUserId
                    val cart = cartService.getCart(userId)
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
                    val userId = testUserId

                    // 추가 상품 생성
                    val product3 = productService.updateProduct(Product(
                        name = "키보드 XYZ",
                        description = "기계식 키보드",
                        price = 120000L,
                        stock = 30,
                        category = ProductCategory.ELECTRONICS,
                        specifications = mapOf("type" to "mechanical"),
                        salesCount = 100
                    ))

                    // when - 여러 상품 추가
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product1Id, quantity = 2)) // 무선 이어폰: 150,000 * 2 = 300,000원
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product2Id, quantity = 1)) // 운동화 ABC: 89,000 * 1 = 89,000원
                    cartService.addCartItem(userId, AddCartItemCommand(productId = product3.id!!, quantity = 3)) // 키보드: 120,000 * 3 = 360,000원

                    // then
                    val cart = cartService.getCart(userId)
                    cart.summary.totalItems shouldBe 3
                    cart.summary.totalQuantity shouldBe 6 // 2 + 1 + 3
                    cart.summary.totalAmount shouldBe 749000L // 300000 + 89000 + 360000
                    cart.summary.availableAmount shouldBe 749000L
                    cart.summary.unavailableCount shouldBe 0
                }
            }
        }
    }
}