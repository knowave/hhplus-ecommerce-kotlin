package com.hhplus.ecommerce.presentation.payment

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import com.hhplus.ecommerce.presentation.payment.dto.*
import com.hhplus.ecommerce.presentation.user.dto.CreateUserRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderResponse
import com.hhplus.ecommerce.presentation.order.dto.OrderItemRequest
import com.hhplus.ecommerce.presentation.product.dto.ProductListResponse
import com.hhplus.ecommerce.model.user.User
import com.hhplus.ecommerce.infrastructure.user.UserRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val userRepository: UserRepository
) : DescribeSpec({

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    // 테스트용 사용자 생성 헬퍼 함수
    fun createTestUser(balance: Long = 2000000L): Long {
        val request = CreateUserRequest(balance = balance)
        val response = restTemplate.postForEntity(url("/users"), request, User::class.java)
        return response.body?.id ?: throw IllegalStateException("Failed to create test user")
    }

    // 테스트용 상품 ID 조회 헬퍼 함수
    fun getTestProductId(): Long {
        val response = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)
        return response.body?.products?.firstOrNull()?.id
            ?: throw IllegalStateException("No products available")
    }

    // 테스트용 주문 생성 헬퍼 함수
    fun createTestOrder(userId: Long, productId: Long): Long {
        val request = CreateOrderRequest(
            userId = userId,
            items = listOf(
                OrderItemRequest(productId = productId, quantity = 1)
            )
        )
        val response = restTemplate.postForEntity(url("/orders"), request, CreateOrderResponse::class.java)
        return response.body?.orderId ?: throw IllegalStateException("Failed to create test order")
    }

    afterEach {
        userRepository.clear()
    }

    describe("Payment API E2E Tests") {

        describe("결제 처리") {
            it("주문에 대한 결제를 처리할 수 있어야 한다") {
                // Given
                val userId = createTestUser(balance = 500000L)
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                val request = ProcessPaymentRequest(userId = userId)

                // When
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    ProcessPaymentResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { payment ->
                    payment.paymentId shouldNotBe null
                    payment.orderId shouldBe orderId
                    payment.userId shouldBe userId
                    (payment.amount > 0) shouldBe true
                    payment.paymentStatus shouldBe "SUCCESS"
                    payment.orderStatus shouldBe "PAID"
                    payment.balance shouldNotBe null
                    payment.balance.previousBalance shouldBe 500000L
                    (payment.balance.paidAmount > 0) shouldBe true
                    (payment.balance.remainingBalance < 2000000L) shouldBe true
                    payment.dataTransmission shouldNotBe null
                    payment.dataTransmission.transmissionId shouldNotBe null
                    payment.dataTransmission.status shouldBe "PENDING"
                    payment.paidAt shouldNotBe null
                }
            }

            it("결제 후 사용자 잔액이 감소해야 한다") {
                // Given
                val initialBalance = 2000000L
                val userId = createTestUser(balance = initialBalance)
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                // When
                val request = ProcessPaymentRequest(userId = userId)
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    ProcessPaymentResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.balance?.let { balance ->
                    balance.previousBalance shouldBe initialBalance
                    balance.remainingBalance shouldBe (initialBalance - balance.paidAmount)
                }
            }

            it("존재하지 않는 주문에 대한 결제 시 404를 반환해야 한다") {
                // Given
                val userId = createTestUser()
                val request = ProcessPaymentRequest(userId = userId)

                // When
                val response = restTemplate.postForEntity(
                    url("/payments/orders/999999/payment"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("다른 사용자의 주문을 결제할 수 없어야 한다") {
                // Given
                val userId1 = createTestUser()
                val userId2 = createTestUser()
                val productId = getTestProductId()
                val orderId = createTestOrder(userId1, productId)

                // When - 다른 사용자가 결제 시도
                val request = ProcessPaymentRequest(userId = userId2)
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            it("잔액이 부족한 경우 결제가 실패해야 한다") {
                // Given - 잔액이 매우 적은 사용자
                val userId = createTestUser(balance = 1000L)
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                // When
                val request = ProcessPaymentRequest(userId = userId)
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("이미 결제된 주문은 다시 결제할 수 없어야 한다") {
                // Given - 결제 완료
                val userId = createTestUser()
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                val request = ProcessPaymentRequest(userId = userId)
                restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    ProcessPaymentResponse::class.java
                )

                // When - 다시 결제 시도
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        describe("결제 정보 조회") {
            it("결제 ID로 결제 상세 정보를 조회할 수 있어야 한다") {
                // Given - 결제 완료
                val userId = createTestUser()
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                val paymentRequest = ProcessPaymentRequest(userId = userId)
                val paymentResponse = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )
                val paymentId = paymentResponse.body?.paymentId

                // When
                val response = restTemplate.getForEntity(
                    url("/payments/$paymentId?userId=$userId"),
                    PaymentDetailResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { payment ->
                    payment.paymentId shouldBe paymentId
                    payment.orderId shouldBe orderId
                    payment.userId shouldBe userId
                    (payment.amount > 0) shouldBe true
                    payment.paymentStatus shouldBe "SUCCESS"
                    payment.paidAt shouldNotBe null
                }
            }

            it("주문 ID로 결제 내역을 조회할 수 있어야 한다") {
                // Given - 결제 완료
                val userId = createTestUser()
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                val paymentRequest = ProcessPaymentRequest(userId = userId)
                restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )

                // When
                val response = restTemplate.getForEntity(
                    url("/payments/orders/$orderId/payment?userId=$userId"),
                    OrderPaymentResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.orderId shouldBe orderId
                    result.orderNumber shouldNotBe null
                    result.payment shouldNotBe null
                    result.payment?.status shouldBe "SUCCESS"
                }
            }

            it("존재하지 않는 결제 ID 조회 시 404를 반환해야 한다") {
                // Given
                val userId = createTestUser()

                // When
                val response = restTemplate.getForEntity(
                    url("/payments/999999?userId=$userId"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("다른 사용자의 결제 정보는 조회할 수 없어야 한다") {
                // Given - 첫 번째 사용자가 결제
                val userId1 = createTestUser()
                val userId2 = createTestUser()
                val productId = getTestProductId()
                val orderId = createTestOrder(userId1, productId)

                val paymentRequest = ProcessPaymentRequest(userId = userId1)
                val paymentResponse = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )
                val paymentId = paymentResponse.body?.paymentId

                // When - 두 번째 사용자가 조회 시도
                val response = restTemplate.getForEntity(
                    url("/payments/$paymentId?userId=$userId2"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        describe("데이터 전송 관리") {
            it("데이터 전송 상태를 조회할 수 있어야 한다") {
                // Given - 결제 완료
                val userId = createTestUser()
                val productId = getTestProductId()
                val orderId = createTestOrder(userId, productId)

                val paymentRequest = ProcessPaymentRequest(userId = userId)
                val paymentResponse = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )
                val transmissionId = paymentResponse.body?.dataTransmission?.transmissionId

                // When
                val response = restTemplate.getForEntity(
                    url("/payments/data-transmissions/$transmissionId"),
                    TransmissionDetailResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { transmission ->
                    transmission.transmissionId shouldBe transmissionId
                    transmission.orderId shouldBe orderId
                    transmission.orderNumber shouldNotBe null
                    transmission.status shouldBe "PENDING"
                    transmission.createdAt shouldNotBe null
                }
            }

            it("존재하지 않는 전송 ID 조회 시 404를 반환해야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/payments/data-transmissions/999999"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("복합 사용 시나리오") {
            it("주문 생성 → 결제 처리 → 결제 조회 → 전송 상태 확인을 연속으로 수행할 수 있어야 한다") {
                // Given
                val userId = createTestUser()
                val productId = getTestProductId()

                // Step 1: 주문 생성
                val orderId = createTestOrder(userId, productId)
                orderId shouldNotBe null

                // Step 2: 결제 처리
                val paymentRequest = ProcessPaymentRequest(userId = userId)
                val paymentResponse = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )
                paymentResponse.statusCode shouldBe HttpStatus.OK
                val paymentId = paymentResponse.body?.paymentId
                val transmissionId = paymentResponse.body?.dataTransmission?.transmissionId

                // Step 3: 결제 정보 조회
                val paymentDetailResponse = restTemplate.getForEntity(
                    url("/payments/$paymentId?userId=$userId"),
                    PaymentDetailResponse::class.java
                )
                paymentDetailResponse.statusCode shouldBe HttpStatus.OK
                paymentDetailResponse.body?.paymentStatus shouldBe "SUCCESS"

                // Step 4: 주문별 결제 내역 조회
                val orderPaymentResponse = restTemplate.getForEntity(
                    url("/payments/orders/$orderId/payment?userId=$userId"),
                    OrderPaymentResponse::class.java
                )
                orderPaymentResponse.statusCode shouldBe HttpStatus.OK
                orderPaymentResponse.body?.payment?.status shouldBe "SUCCESS"

                // Step 5: 데이터 전송 상태 확인
                val transmissionResponse = restTemplate.getForEntity(
                    url("/payments/data-transmissions/$transmissionId"),
                    TransmissionDetailResponse::class.java
                )
                transmissionResponse.statusCode shouldBe HttpStatus.OK
                transmissionResponse.body?.status shouldBe "PENDING"
            }

            it("여러 사용자가 동시에 각자의 주문을 결제할 수 있어야 한다") {
                // Given
                val userId1 = createTestUser()
                val userId2 = createTestUser()
                val productId = getTestProductId()

                val orderId1 = createTestOrder(userId1, productId)
                val orderId2 = createTestOrder(userId2, productId)

                // When - 첫 번째 사용자 결제
                val request1 = ProcessPaymentRequest(userId = userId1)
                val response1 = restTemplate.postForEntity(
                    url("/payments/orders/$orderId1/payment"),
                    request1,
                    ProcessPaymentResponse::class.java
                )

                // When - 두 번째 사용자 결제
                val request2 = ProcessPaymentRequest(userId = userId2)
                val response2 = restTemplate.postForEntity(
                    url("/payments/orders/$orderId2/payment"),
                    request2,
                    ProcessPaymentResponse::class.java
                )

                // Then
                response1.statusCode shouldBe HttpStatus.OK
                response2.statusCode shouldBe HttpStatus.OK
                response1.body?.userId shouldBe userId1
                response2.body?.userId shouldBe userId2
                response1.body?.orderId shouldBe orderId1
                response2.body?.orderId shouldBe orderId2
            }
        }
    }
})
