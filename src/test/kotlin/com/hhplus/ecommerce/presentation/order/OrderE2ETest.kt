package com.hhplus.ecommerce.presentation.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import com.hhplus.ecommerce.presentation.order.dto.*
import com.hhplus.ecommerce.presentation.user.dto.CreateUserRequest
import com.hhplus.ecommerce.presentation.product.dto.ProductListResponse
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.UserRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val userRepository: UserRepository
) : DescribeSpec({

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    // 테스트용 사용자 생성 헬퍼 함수
    fun createTestUser(balance: Long = 200000L): Long {
        val request = CreateUserRequest(balance = balance)
        val response = restTemplate.postForEntity(url("/users"), request, User::class.java)
        println(response)
        return response.body?.id ?: throw IllegalStateException("Failed to create test user")
    }

    // 테스트용 상품 ID 조회 헬퍼 함수
    fun getTestProducts(count: Int = 2): List<Long> {
        val response = restTemplate.getForEntity(url("/products?size=$count"), ProductListResponse::class.java)
        return response.body?.products?.map { it.id } ?: emptyList()
    }

    afterEach {
        userRepository.clear()
    }

    describe("Order API E2E Tests") {

        describe("주문 생성") {
            it("상품을 주문할 수 있어야 한다") {
                // Given
                val userId = createTestUser(balance = 200000L)
                val productIds = getTestProducts(2)

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1),
                        OrderItemRequest(productId = productIds[1], quantity = 2)
                    )
                )

                // When
                val response = restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body shouldNotBe null
                response.body?.let { order ->
                    order.orderId shouldNotBe null
                    order.userId shouldBe userId
                    order.orderNumber shouldNotBe null
                    order.items shouldHaveSize 2
                    order.status shouldBe "PENDING"
                    order.pricing shouldNotBe null
                    (order.pricing.totalAmount > 0) shouldBe true
                    (order.pricing.finalAmount > 0) shouldBe true
                    order.createdAt shouldNotBe null
                }
            }

            it("주문 생성 시 상품 정보가 정확히 반영되어야 한다") {
                // Given
                val userId = createTestUser(200000L)
                val productIds = getTestProducts(1)

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 3)
                    )
                )

                // When
                val response = restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.items?.get(0)?.let { item ->
                    item.productId shouldBe productIds[0]
                    item.quantity shouldBe 3
                    item.productName shouldNotBe null
                    (item.unitPrice > 0) shouldBe true
                    item.subtotal shouldBe item.unitPrice * item.quantity
                }
            }

            it("여러 상품을 함께 주문할 수 있어야 한다") {
                // Given
                val userId = createTestUser(balance = 1000000L)
                val productIds = getTestProducts(3)

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1),
                        OrderItemRequest(productId = productIds[1], quantity = 2),
                        OrderItemRequest(productId = productIds[2], quantity = 1)
                    )
                )

                // When
                val response = restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.items?.size shouldBe 3
            }

            it("존재하지 않는 사용자로 주문 시 에러가 발생해야 한다") {
                // Given
                val productIds = getTestProducts(1)

                val request = CreateOrderRequest(
                    userId = 999999L,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )

                // When
                val response = restTemplate.postForEntity(url("/orders"), request, String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("존재하지 않는 상품으로 주문 시 에러가 발생해야 한다") {
                // Given
                val userId = createTestUser(2000000L)

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = 999999L, quantity = 1)
                    )
                )

                // When
                val response = restTemplate.postForEntity(url("/orders"), request, String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("주문 상세 조회") {
            it("주문 ID로 주문 상세 정보를 조회할 수 있어야 한다") {
                // Given - 주문 생성
                val userId = createTestUser()
                val productIds = getTestProducts(1)

                val createRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                val orderId = createResponse.body?.orderId

                // When
                val response = restTemplate.getForEntity(
                    url("/orders/$orderId?userId=$userId"),
                    OrderDetailResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { order ->
                    order.orderId shouldBe orderId
                    order.userId shouldBe userId
                    order.orderNumber shouldNotBe null
                    order.items.shouldNotBeEmpty()
                    order.pricing shouldNotBe null
                    order.status shouldNotBe null
                    order.createdAt shouldNotBe null
                    order.updatedAt shouldNotBe null
                }
            }

            it("다른 사용자의 주문을 조회할 수 없어야 한다") {
                // Given - 첫 번째 사용자가 주문 생성
                val userId1 = createTestUser()
                val userId2 = createTestUser()
                val productIds = getTestProducts(1)

                val createRequest = CreateOrderRequest(
                    userId = userId1,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                val orderId = createResponse.body?.orderId

                // When - 두 번째 사용자가 조회 시도
                val response = restTemplate.getForEntity(
                    url("/orders/$orderId?userId=$userId2"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            it("존재하지 않는 주문 조회 시 404를 반환해야 한다") {
                // Given
                val userId = createTestUser()

                // When
                val response = restTemplate.getForEntity(
                    url("/orders/999999?userId=$userId"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("주문 목록 조회") {
            it("사용자의 주문 목록을 조회할 수 있어야 한다") {
                // Given - 여러 주문 생성
                val userId = createTestUser(balance = 2000000L)
                val productIds = getTestProducts(1)

                repeat(3) {
                    val request = CreateOrderRequest(
                        userId = userId,
                        items = listOf(
                            OrderItemRequest(productId = productIds[0], quantity = 1)
                        )
                    )
                    restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)
                }

                // When
                val response = restTemplate.getForEntity(
                    url("/orders?userId=$userId"),
                    OrderListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.orders shouldHaveSize 3
                    result.pagination shouldNotBe null
                }
            }

            it("주문 목록을 페이지네이션하여 조회할 수 있어야 한다") {
                // Given
                val userId = createTestUser(balance = 3000000L)
                val productIds = getTestProducts(1)

                repeat(5) {
                    val request = CreateOrderRequest(
                        userId = userId,
                        items = listOf(
                            OrderItemRequest(productId = productIds[0], quantity = 1)
                        )
                    )
                    restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)
                }

                // When - 첫 페이지 조회
                val page0Response = restTemplate.getForEntity(
                    url("/orders?userId=$userId&page=0&size=3"),
                    OrderListResponse::class.java
                )

                // Then
                page0Response.statusCode shouldBe HttpStatus.OK
                page0Response.body?.let { result ->
                    result.orders shouldHaveSize 3
                    result.pagination.currentPage shouldBe 0
                    result.pagination.size shouldBe 3
                }
            }

            it("주문이 없는 사용자는 빈 목록을 반환해야 한다") {
                // Given
                val userId = createTestUser()

                // When
                val response = restTemplate.getForEntity(
                    url("/orders?userId=$userId"),
                    OrderListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.orders?.size shouldBe 0
            }

            it("상태 필터링으로 주문 목록을 조회할 수 있어야 한다") {
                // Given
                val userId = createTestUser(balance = 2000000L)
                val productIds = getTestProducts(1)

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)

                // When - PENDING 상태 주문 조회
                val response = restTemplate.getForEntity(
                    url("/orders?userId=$userId&status=PENDING"),
                    OrderListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.orders?.forEach { order ->
                    order.status shouldBe "PENDING"
                }
            }
        }

        describe("주문 취소") {
            it("주문을 취소할 수 있어야 한다") {
                // Given - 주문 생성
                val userId = createTestUser()
                val productIds = getTestProducts(1)

                val createRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                val orderId = createResponse.body?.orderId

                // When - 주문 취소
                val cancelRequest = CancelOrderRequest(
                    userId = userId,
                    reason = "단순 변심"
                )
                val cancelResponse = restTemplate.postForEntity(
                    url("/orders/$orderId/cancel"),
                    cancelRequest,
                    CancelOrderResponse::class.java
                )

                // Then
                cancelResponse.statusCode shouldBe HttpStatus.OK
                cancelResponse.body shouldNotBe null
                cancelResponse.body?.let { cancel ->
                    cancel.orderId shouldBe orderId
                    cancel.status shouldBe "CANCELLED"
                    cancel.cancelledAt shouldNotBe null
                    cancel.refund shouldNotBe null
                    cancel.refund.restoredStock.shouldNotBeEmpty()
                }
            }

            it("주문 취소 시 재고가 복원되어야 한다") {
                // Given
                val userId = createTestUser()
                val productIds = getTestProducts(1)

                val createRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 2)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                val orderId = createResponse.body?.orderId

                // When
                val cancelRequest = CancelOrderRequest(userId = userId)
                val cancelResponse = restTemplate.postForEntity(
                    url("/orders/$orderId/cancel"),
                    cancelRequest,
                    CancelOrderResponse::class.java
                )

                // Then
                cancelResponse.body?.refund?.restoredStock?.let { restoredItems ->
                    restoredItems shouldHaveSize 1
                    restoredItems[0].productId shouldBe productIds[0]
                    restoredItems[0].quantity shouldBe 2
                }
            }

            it("다른 사용자의 주문은 취소할 수 없어야 한다") {
                // Given
                val userId1 = createTestUser()
                val userId2 = createTestUser()
                val productIds = getTestProducts(1)

                val createRequest = CreateOrderRequest(
                    userId = userId1,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                val orderId = createResponse.body?.orderId

                // When - 다른 사용자가 취소 시도
                val cancelRequest = CancelOrderRequest(userId = userId2)
                val cancelResponse = restTemplate.postForEntity(
                    url("/orders/$orderId/cancel"),
                    cancelRequest,
                    String::class.java
                )

                // Then
                cancelResponse.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            it("이미 취소된 주문은 다시 취소할 수 없어야 한다") {
                // Given - 주문 생성 및 취소
                val userId = createTestUser()
                val productIds = getTestProducts(1)

                val createRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                val orderId = createResponse.body?.orderId

                val cancelRequest = CancelOrderRequest(userId = userId)
                restTemplate.postForEntity(url("/orders/$orderId/cancel"), cancelRequest, CancelOrderResponse::class.java)

                // When - 다시 취소 시도
                val secondCancelResponse = restTemplate.postForEntity(
                    url("/orders/$orderId/cancel"),
                    cancelRequest,
                    String::class.java
                )

                // Then
                secondCancelResponse.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        describe("복합 사용 시나리오") {
            it("주문 생성 후 상세 조회, 목록 조회, 취소를 순차적으로 수행할 수 있어야 한다") {
                // Given
                val userId = createTestUser()
                val productIds = getTestProducts(2)

                // Step 1: 주문 생성
                val createRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1),
                        OrderItemRequest(productId = productIds[1], quantity = 2)
                    )
                )
                val createResponse = restTemplate.postForEntity(url("/orders"), createRequest, CreateOrderResponse::class.java)
                createResponse.statusCode shouldBe HttpStatus.CREATED
                val orderId = createResponse.body?.orderId

                // Step 2: 주문 상세 조회
                val detailResponse = restTemplate.getForEntity(
                    url("/orders/$orderId?userId=$userId"),
                    OrderDetailResponse::class.java
                )
                detailResponse.statusCode shouldBe HttpStatus.OK
                detailResponse.body?.status shouldBe "PENDING"

                // Step 3: 주문 목록 조회
                val listResponse = restTemplate.getForEntity(
                    url("/orders?userId=$userId"),
                    OrderListResponse::class.java
                )
                listResponse.statusCode shouldBe HttpStatus.OK
                listResponse.body?.orders?.size shouldBe 1

                // Step 4: 주문 취소
                val cancelRequest = CancelOrderRequest(userId = userId)
                val cancelResponse = restTemplate.postForEntity(
                    url("/orders/$orderId/cancel"),
                    cancelRequest,
                    CancelOrderResponse::class.java
                )
                cancelResponse.statusCode shouldBe HttpStatus.OK
                cancelResponse.body?.status shouldBe "CANCELLED"

                // Step 5: 취소된 주문 확인
                val finalDetailResponse = restTemplate.getForEntity(
                    url("/orders/$orderId?userId=$userId"),
                    OrderDetailResponse::class.java
                )
                finalDetailResponse.body?.status shouldBe "CANCELLED"
            }

            it("여러 사용자가 동시에 주문을 생성할 수 있어야 한다") {
                // Given
                val userId1 = createTestUser(balance = 1000000L)
                val userId2 = createTestUser(balance = 1000000L)
                val productIds = getTestProducts(2)

                // When - 두 사용자가 각각 주문 생성
                val request1 = CreateOrderRequest(
                    userId = userId1,
                    items = listOf(
                        OrderItemRequest(productId = productIds[0], quantity = 1)
                    )
                )
                val response1 = restTemplate.postForEntity(url("/orders"), request1, CreateOrderResponse::class.java)

                val request2 = CreateOrderRequest(
                    userId = userId2,
                    items = listOf(
                        OrderItemRequest(productId = productIds[1], quantity = 1)
                    )
                )
                val response2 = restTemplate.postForEntity(url("/orders"), request2, CreateOrderResponse::class.java)

                // Then
                response1.statusCode shouldBe HttpStatus.CREATED
                response2.statusCode shouldBe HttpStatus.CREATED
                response1.body?.userId shouldBe userId1
                response2.body?.userId shouldBe userId2

                // 각 사용자는 자신의 주문만 조회 가능
                val list1 = restTemplate.getForEntity(url("/orders?userId=$userId1"), OrderListResponse::class.java)
                val list2 = restTemplate.getForEntity(url("/orders?userId=$userId2"), OrderListResponse::class.java)

                list1.body?.orders?.size shouldBe 1
                list2.body?.orders?.size shouldBe 1
            }
        }
    }
})
