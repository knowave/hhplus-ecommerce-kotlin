package com.hhplus.ecommerce.presentation.cart

import com.hhplus.ecommerce.presentation.cart.dto.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate
) : DescribeSpec({

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    describe("Cart API E2E Tests") {

        describe("장바구니 조회") {
            it("빈 장바구니를 조회할 수 있어야 한다") {
                // Given - 사용자 2는 장바구니가 비어있음
                val userId = 2L

                // When
                val response = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { cart ->
                    cart.userId shouldBe userId
                    cart.items.shouldBeEmpty()
                    cart.summary.totalItems shouldBe 0
                    cart.summary.totalQuantity shouldBe 0
                    cart.summary.totalAmount shouldBe 0L
                    cart.summary.availableAmount shouldBe 0L
                    cart.summary.unavailableCount shouldBe 0
                }
            }

            it("상품이 담긴 장바구니를 조회할 수 있어야 한다") {
                // Given - 사용자 1은 초기 샘플 데이터로 장바구니에 상품이 담겨있음
                val userId = 1L

                // When
                val response = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { cart ->
                    cart.userId shouldBe userId
                    cart.items.shouldNotBeEmpty()

                    // 각 아이템 검증
                    cart.items.forEach { item ->
                        item.cartItemId shouldNotBe null
                        item.productId shouldNotBe null
                        item.productName shouldNotBe null
                        item.price shouldNotBe null
                        (item.price > 0) shouldBe true
                        (item.quantity >= 1) shouldBe true
                        item.subtotal shouldBe (item.price * item.quantity)
                        (item.stock >= 0) shouldBe true
                        item.addedAt shouldNotBe null
                    }

                    // 요약 정보 검증
                    cart.summary.totalItems shouldBe cart.items.size
                    cart.summary.totalQuantity shouldBe cart.items.sumOf { it.quantity }
                    cart.summary.totalAmount shouldBe cart.items.sumOf { it.subtotal }
                    cart.summary.availableAmount shouldBe cart.items.filter { it.isAvailable }.sumOf { it.subtotal }
                }
            }

            it("존재하지 않는 사용자의 장바구니 조회 시 404를 반환해야 한다") {
                // Given
                val invalidUserId = 999L

                // When
                val response = restTemplate.getForEntity(url("/carts/$invalidUserId"), String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("상품 추가") {
            it("장바구니에 새로운 상품을 추가할 수 있어야 한다") {
                // Given
                val userId = 2L // 빈 장바구니를 가진 사용자
                val request = AddCartItemRequest(productId = 1L, quantity = 2)

                // When
                val response = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    request,
                    AddCartItemResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.cartItemId shouldNotBe null
                    result.productId shouldBe 1L
                    result.productName shouldNotBe null
                    result.price shouldNotBe null
                    (result.price > 0) shouldBe true
                    result.quantity shouldBe 2
                    result.subtotal shouldBe (result.price * 2)
                    result.addedAt shouldNotBe null
                }

                // Clean up - 장바구니 비우기
                restTemplate.delete(url("/carts/$userId"))
            }

            it("이미 장바구니에 있는 상품을 추가하면 수량이 증가해야 한다") {
                // Given - 먼저 상품 추가
                val userId = 2L
                val productId = 1L
                val initialRequest = AddCartItemRequest(productId = productId, quantity = 2)

                val initialResponse = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    initialRequest,
                    AddCartItemResponse::class.java
                )
                initialResponse.statusCode shouldBe HttpStatus.OK
                val initialQuantity = initialResponse.body?.quantity

                // When - 같은 상품 다시 추가
                val additionalRequest = AddCartItemRequest(productId = productId, quantity = 3)
                val response = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    additionalRequest,
                    AddCartItemResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.productId shouldBe productId
                    result.quantity shouldBe (initialQuantity!! + 3) // 2 + 3 = 5
                    result.subtotal shouldBe (result.price * result.quantity)
                }

                // Clean up
                restTemplate.delete(url("/carts/$userId"))
            }

            it("재고보다 많은 수량을 추가하면 400을 반환해야 한다") {
                // Given
                val userId = 2L
                val productId = 7L // 운동화 ABC, 재고 45개
                val request = AddCartItemRequest(productId = productId, quantity = 100) // 100 > 45

                // When
                val response = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("수량이 0 이하면 400을 반환해야 한다") {
                // Given
                val userId = 2L
                val request = AddCartItemRequest(productId = 1L, quantity = 0)

                // When
                val response = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("존재하지 않는 상품을 추가하면 404를 반환해야 한다") {
                // Given
                val userId = 2L
                val request = AddCartItemRequest(productId = 999L, quantity = 1)

                // When
                val response = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("최대 수량(100)을 초과하면 400을 반환해야 한다") {
                // Given
                val userId = 2L
                val request = AddCartItemRequest(productId = 1L, quantity = 101)

                // When
                val response = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST

                // Clean up
                restTemplate.delete(url("/carts/$userId"))
            }
        }

        describe("수량 변경") {
            it("장바구니 아이템의 수량을 변경할 수 있어야 한다") {
                // Given - 먼저 상품 추가
                val userId = 2L
                val addRequest = AddCartItemRequest(productId = 1L, quantity = 2)
                val addResponse = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    addRequest,
                    AddCartItemResponse::class.java
                )
                val cartItemId = addResponse.body?.cartItemId!!

                // When - 수량 변경
                val updateRequest = UpdateCartItemRequest(quantity = 5)
                val response = restTemplate.exchange(
                    url("/carts/$userId/items/$cartItemId"),
                    HttpMethod.PATCH,
                    HttpEntity(updateRequest),
                    UpdateCartItemResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.cartItemId shouldBe cartItemId
                    result.quantity shouldBe 5
                    result.subtotal shouldBe (result.price * 5)
                    result.updatedAt shouldNotBe null
                }

                // Clean up
                restTemplate.delete(url("/carts/$userId"))
            }

            it("수량을 1로 변경할 수 있어야 한다") {
                // Given
                val userId = 2L
                val addRequest = AddCartItemRequest(productId = 1L, quantity = 10)
                val addResponse = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    addRequest,
                    AddCartItemResponse::class.java
                )
                val cartItemId = addResponse.body?.cartItemId!!

                // When
                val updateRequest = UpdateCartItemRequest(quantity = 1)
                val response = restTemplate.exchange(
                    url("/carts/$userId/items/$cartItemId"),
                    HttpMethod.PATCH,
                    HttpEntity(updateRequest),
                    UpdateCartItemResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.quantity shouldBe 1

                // Clean up
                restTemplate.delete(url("/carts/$userId"))
            }

            it("존재하지 않는 장바구니 아이템의 수량을 변경하면 404를 반환해야 한다") {
                // Given
                val userId = 2L
                val invalidCartItemId = 999L
                val updateRequest = UpdateCartItemRequest(quantity = 5)

                // When
                val response = restTemplate.exchange(
                    url("/carts/$userId/items/$invalidCartItemId"),
                    HttpMethod.PATCH,
                    HttpEntity(updateRequest),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("수량을 최대값(100)을 초과하여 변경하면 400을 반환해야 한다") {
                // Given
                val userId = 2L
                val addRequest = AddCartItemRequest(productId = 1L, quantity = 2)
                val addResponse = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    addRequest,
                    AddCartItemResponse::class.java
                )
                val cartItemId = addResponse.body?.cartItemId!!

                // When
                val updateRequest = UpdateCartItemRequest(quantity = 101)
                val response = restTemplate.exchange(
                    url("/carts/$userId/items/$cartItemId"),
                    HttpMethod.PATCH,
                    HttpEntity(updateRequest),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST

                // Clean up
                restTemplate.delete(url("/carts/$userId"))
            }
        }

        describe("아이템 삭제") {
            it("장바구니에서 아이템을 삭제할 수 있어야 한다") {
                // Given - 먼저 상품 추가
                val userId = 2L
                val addRequest = AddCartItemRequest(productId = 1L, quantity = 2)
                val addResponse = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    addRequest,
                    AddCartItemResponse::class.java
                )
                val cartItemId = addResponse.body?.cartItemId!!

                // When - 아이템 삭제
                restTemplate.delete(url("/carts/$userId/items/$cartItemId"))

                // Then - 삭제 후 장바구니 조회하여 확인
                val cartResponse = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartResponse.body?.items?.none { it.cartItemId == cartItemId } shouldBe true
            }

            it("존재하지 않는 장바구니 아이템을 삭제하면 404를 반환해야 한다") {
                // Given
                val userId = 2L
                val invalidCartItemId = 999L

                // When
                val response = restTemplate.exchange(
                    url("/carts/$userId/items/$invalidCartItemId"),
                    HttpMethod.DELETE,
                    null,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("장바구니 비우기") {
            it("장바구니를 전체 비울 수 있어야 한다") {
                // Given - 먼저 상품 여러 개 추가
                val userId = 2L
                restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = 1L, quantity = 2),
                    AddCartItemResponse::class.java
                )
                restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = 2L, quantity = 1),
                    AddCartItemResponse::class.java
                )

                // When - 장바구니 비우기
                restTemplate.delete(url("/carts/$userId"))

                // Then - 빈 장바구니 확인
                val cartResponse = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartResponse.statusCode shouldBe HttpStatus.OK
                cartResponse.body?.items.shouldBeEmpty()
                cartResponse.body?.summary?.totalItems shouldBe 0
            }

            it("이미 빈 장바구니를 비워도 정상 처리되어야 한다") {
                // Given
                val userId = 2L
                restTemplate.delete(url("/carts/$userId")) // 먼저 비우기

                // When
                restTemplate.delete(url("/carts/$userId")) // 다시 비우기

                // Then - 여전히 빈 장바구니
                val cartResponse = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartResponse.statusCode shouldBe HttpStatus.OK
                cartResponse.body?.items.shouldBeEmpty()
            }

            it("존재하지 않는 사용자의 장바구니를 비우면 404를 반환해야 한다") {
                // Given
                val invalidUserId = 999L

                // When
                val response = restTemplate.exchange(
                    url("/carts/$invalidUserId"),
                    HttpMethod.DELETE,
                    null,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("복합 사용 시나리오") {
            it("장바구니 전체 플로우를 순차적으로 수행할 수 있어야 한다") {
                // Given
                val userId = 3L

                // 1. 빈 장바구니 확인
                val emptyCartResponse = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                emptyCartResponse.statusCode shouldBe HttpStatus.OK
                emptyCartResponse.body?.items.shouldBeEmpty()

                // 2. 상품 3개 추가
                val addResponse1 = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = 1L, quantity = 2),
                    AddCartItemResponse::class.java
                )
                addResponse1.statusCode shouldBe HttpStatus.OK
                val cartItemId1 = addResponse1.body?.cartItemId!!

                val addResponse2 = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = 5L, quantity = 1),
                    AddCartItemResponse::class.java
                )
                addResponse2.statusCode shouldBe HttpStatus.OK
                val cartItemId2 = addResponse2.body?.cartItemId!!

                val addResponse3 = restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = 10L, quantity = 3),
                    AddCartItemResponse::class.java
                )
                addResponse3.statusCode shouldBe HttpStatus.OK

                // 3. 장바구니 조회 - 3개 아이템 확인
                val cartWithItems = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartWithItems.statusCode shouldBe HttpStatus.OK
                cartWithItems.body?.items?.size shouldBe 3
                cartWithItems.body?.summary?.totalItems shouldBe 3
                cartWithItems.body?.summary?.totalQuantity shouldBe 6 // 2 + 1 + 3

                // 4. 첫 번째 아이템 수량 변경 (2 -> 5)
                val updateResponse = restTemplate.exchange(
                    url("/carts/$userId/items/$cartItemId1"),
                    HttpMethod.PATCH,
                    HttpEntity(UpdateCartItemRequest(quantity = 5)),
                    UpdateCartItemResponse::class.java
                )
                updateResponse.statusCode shouldBe HttpStatus.OK
                updateResponse.body?.quantity shouldBe 5

                // 5. 장바구니 재조회 - 수량 변경 확인
                val cartAfterUpdate = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartAfterUpdate.body?.summary?.totalQuantity shouldBe 9 // 5 + 1 + 3

                // 6. 두 번째 아이템 삭제
                restTemplate.delete(url("/carts/$userId/items/$cartItemId2"))

                // 7. 장바구니 재조회 - 아이템 2개 확인
                val cartAfterDelete = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartAfterDelete.body?.items?.size shouldBe 2
                cartAfterDelete.body?.summary?.totalQuantity shouldBe 8 // 5 + 3

                // 8. 장바구니 전체 비우기
                restTemplate.delete(url("/carts/$userId"))

                // 9. 빈 장바구니 확인
                val finalCartResponse = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                finalCartResponse.body?.items.shouldBeEmpty()
                finalCartResponse.body?.summary?.totalItems shouldBe 0
            }

            it("같은 상품을 여러 번 추가하면 수량이 누적되어야 한다") {
                // Given
                val userId = 3L
                val productId = 1L
                restTemplate.delete(url("/carts/$userId")) // 먼저 비우기

                // When - 같은 상품 3번 추가
                restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = productId, quantity = 2),
                    AddCartItemResponse::class.java
                )
                restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = productId, quantity = 3),
                    AddCartItemResponse::class.java
                )
                restTemplate.postForEntity(
                    url("/carts/$userId/items"),
                    AddCartItemRequest(productId = productId, quantity = 5),
                    AddCartItemResponse::class.java
                )

                // Then - 장바구니에는 1개 아이템만 있고 수량은 10
                val cartResponse = restTemplate.getForEntity(url("/carts/$userId"), CartResponse::class.java)
                cartResponse.body?.items?.size shouldBe 1
                cartResponse.body?.items?.first()?.quantity shouldBe 10 // 2 + 3 + 5
                cartResponse.body?.summary?.totalItems shouldBe 1

                // Clean up
                restTemplate.delete(url("/carts/$userId"))
            }

            it("여러 사용자의 장바구니가 독립적으로 관리되어야 한다") {
                // Given
                val user1Id = 2L
                val user2Id = 3L
                restTemplate.delete(url("/carts/$user1Id"))
                restTemplate.delete(url("/carts/$user2Id"))

                // When - 사용자1은 상품 1, 2 추가
                restTemplate.postForEntity(
                    url("/carts/$user1Id/items"),
                    AddCartItemRequest(productId = 1L, quantity = 1),
                    AddCartItemResponse::class.java
                )
                restTemplate.postForEntity(
                    url("/carts/$user1Id/items"),
                    AddCartItemRequest(productId = 2L, quantity = 1),
                    AddCartItemResponse::class.java
                )

                // When - 사용자2는 상품 5, 10 추가
                restTemplate.postForEntity(
                    url("/carts/$user2Id/items"),
                    AddCartItemRequest(productId = 5L, quantity = 2),
                    AddCartItemResponse::class.java
                )
                restTemplate.postForEntity(
                    url("/carts/$user2Id/items"),
                    AddCartItemRequest(productId = 10L, quantity = 3),
                    AddCartItemResponse::class.java
                )

                // Then - 각 사용자의 장바구니가 독립적으로 관리됨
                val cart1 = restTemplate.getForEntity(url("/carts/$user1Id"), CartResponse::class.java)
                cart1.body?.items?.size shouldBe 2
                cart1.body?.items?.map { it.productId } shouldBe listOf(1L, 2L)

                val cart2 = restTemplate.getForEntity(url("/carts/$user2Id"), CartResponse::class.java)
                cart2.body?.items?.size shouldBe 2
                cart2.body?.items?.map { it.productId } shouldBe listOf(5L, 10L)

                // Clean up
                restTemplate.delete(url("/carts/$user1Id"))
                restTemplate.delete(url("/carts/$user2Id"))
            }
        }
    }
})