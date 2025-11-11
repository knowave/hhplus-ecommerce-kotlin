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
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.repository.UserRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val userService: com.hhplus.ecommerce.application.user.UserService,
    private val productService: com.hhplus.ecommerce.application.product.ProductService,
    private val paymentJpaRepository: com.hhplus.ecommerce.domain.payment.repository.PaymentJpaRepository,
    private val transmissionJpaRepository: com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
) : DescribeSpec({

    // 테스트용 데이터 ID
    var user1Id: java.util.UUID? = null
    var user2Id: java.util.UUID? = null
    var product1Id: java.util.UUID? = null
    var product2Id: java.util.UUID? = null

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    beforeSpec {
        // 테스트용 사용자 2명 생성
        val createUser1Command = com.hhplus.ecommerce.application.user.dto.CreateUserCommand(balance = 2000000L)
        val createUser2Command = com.hhplus.ecommerce.application.user.dto.CreateUserCommand(balance = 2000000L)

        val savedUser1 = userService.createUser(createUser1Command)
        val savedUser2 = userService.createUser(createUser2Command)

        // 테스트용 상품 2개 생성
        val product1 = com.hhplus.ecommerce.domain.product.entity.Product(
            name = "노트북 ABC",
            description = "고성능 노트북으로 멀티태스킹에 최적화되어 있습니다.",
            price = 100000L,
            stock = 100,
            category = com.hhplus.ecommerce.domain.product.entity.ProductCategory.ELECTRONICS,
            specifications = mapOf("cpu" to "Intel i7", "ram" to "16GB", "storage" to "512GB SSD"),
            salesCount = 0
        )

        val product2 = com.hhplus.ecommerce.domain.product.entity.Product(
            name = "스마트폰 XYZ",
            description = "최신 플래그십 스마트폰",
            price = 80000L,
            stock = 150,
            category = com.hhplus.ecommerce.domain.product.entity.ProductCategory.ELECTRONICS,
            specifications = mapOf("display" to "6.5inch OLED", "camera" to "108MP", "battery" to "5000mAh"),
            salesCount = 0
        )

        val savedProduct1 = productService.updateProduct(product1)
        val savedProduct2 = productService.updateProduct(product2)

        user1Id = savedUser1.id!!
        user2Id = savedUser2.id!!
        product1Id = savedProduct1.id!!
        product2Id = savedProduct2.id!!
    }

    afterEach {
        // 결제 및 전송 데이터 정리
        transmissionJpaRepository.deleteAll()
        paymentJpaRepository.deleteAll()
    }

    describe("Payment API E2E Tests") {

        describe("결제 처리") {
            it("주문에 대한 결제를 처리할 수 있어야 한다") {
                // Given - 주문 생성
                val userId = user1Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

                val request = ProcessPaymentRequest(userId = userId)

                // When - 결제 처리
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    ProcessPaymentResponse::class.java
                )

                // Then - 결제 검증
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { payment ->
                    payment.paymentId shouldNotBe null
                    payment.orderId shouldBe orderId
                    payment.userId shouldBe userId
                    payment.amount shouldBe 100000L
                    payment.paymentStatus shouldBe "SUCCESS"
                    payment.orderStatus shouldBe "PAID"
                    payment.balance shouldNotBe null
                    payment.balance.previousBalance shouldBe 2000000L
                    payment.balance.paidAmount shouldBe 100000L
                    payment.balance.remainingBalance shouldBe 1900000L
                    payment.dataTransmission shouldNotBe null
                    payment.dataTransmission.transmissionId shouldNotBe null
                    payment.dataTransmission.status shouldBe "PENDING"
                    payment.paidAt shouldNotBe null
                }
            }

            it("결제 후 사용자 잔액이 감소해야 한다") {
                // Given - 주문 생성
                val userId = user1Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 2)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

                // When - 결제 처리
                val request = ProcessPaymentRequest(userId = userId)
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    ProcessPaymentResponse::class.java
                )

                // Then - 잔액 확인
                response.statusCode shouldBe HttpStatus.OK
                response.body?.balance?.let { balance ->
                    balance.previousBalance shouldBe 2000000L
                    balance.paidAmount shouldBe 200000L
                    balance.remainingBalance shouldBe 1800000L
                }
            }

            it("존재하지 않는 주문에 대한 결제 시 404를 반환해야 한다") {
                // Given
                val userId = user1Id!!
                val nonExistentOrderId = java.util.UUID.randomUUID()
                val request = ProcessPaymentRequest(userId = userId)

                // When
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$nonExistentOrderId/payment"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("다른 사용자의 주문을 결제할 수 없어야 한다") {
                // Given - 첫 번째 사용자가 주문 생성
                val userId1 = user1Id!!
                val userId2 = user2Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId1,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

                // When - 두 번째 사용자가 결제 시도
                val request = ProcessPaymentRequest(userId = userId2)
                val response = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            it("이미 결제된 주문은 다시 결제할 수 없어야 한다") {
                // Given - 주문 생성 및 결제 완료
                val userId = user1Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

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
                // Given - 주문 생성 및 결제 완료
                val userId = user1Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

                val paymentRequest = ProcessPaymentRequest(userId = userId)
                val paymentResponse = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )
                val paymentId = paymentResponse.body?.paymentId

                // When - 결제 상세 조회
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
                    payment.amount shouldBe 100000L
                    payment.paymentStatus shouldBe "SUCCESS"
                    payment.paidAt shouldNotBe null
                }
            }

            it("주문 ID로 결제 내역을 조회할 수 있어야 한다") {
                // Given - 주문 생성 및 결제 완료
                val userId = user1Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

                val paymentRequest = ProcessPaymentRequest(userId = userId)
                restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )

                // When - 주문별 결제 내역 조회
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
                val userId = user1Id!!
                val nonExistentPaymentId = java.util.UUID.randomUUID()

                // When
                val response = restTemplate.getForEntity(
                    url("/payments/$nonExistentPaymentId?userId=$userId"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("다른 사용자의 결제 정보는 조회할 수 없어야 한다") {
                // Given - 첫 번째 사용자가 주문 생성 및 결제
                val userId1 = user1Id!!
                val userId2 = user2Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId1,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

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
                // Given - 주문 생성 및 결제 완료
                val userId = user1Id!!
                val productId = product1Id!!

                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!

                val paymentRequest = ProcessPaymentRequest(userId = userId)
                val paymentResponse = restTemplate.postForEntity(
                    url("/payments/orders/$orderId/payment"),
                    paymentRequest,
                    ProcessPaymentResponse::class.java
                )
                val transmissionId = paymentResponse.body?.dataTransmission?.transmissionId

                // When - 전송 상태 조회
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
                // Given
                val nonExistentTransmissionId = java.util.UUID.randomUUID()

                // When
                val response = restTemplate.getForEntity(
                    url("/payments/data-transmissions/$nonExistentTransmissionId"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("복합 사용 시나리오") {
            it("주문 생성 → 결제 처리 → 결제 조회 → 전송 상태 확인을 연속으로 수행할 수 있어야 한다") {
                // Given
                val userId = user1Id!!
                val productId = product1Id!!

                // Step 1: 주문 생성
                val orderRequest = CreateOrderRequest(
                    userId = userId,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse = restTemplate.postForEntity(url("/orders"), orderRequest, CreateOrderResponse::class.java)
                val orderId = orderResponse.body?.orderId!!
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
                val userId1 = user1Id!!
                val userId2 = user2Id!!
                val productId = product1Id!!

                // 첫 번째 사용자 주문 생성
                val orderRequest1 = CreateOrderRequest(
                    userId = userId1,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse1 = restTemplate.postForEntity(url("/orders"), orderRequest1, CreateOrderResponse::class.java)
                val orderId1 = orderResponse1.body?.orderId!!

                // 두 번째 사용자 주문 생성
                val orderRequest2 = CreateOrderRequest(
                    userId = userId2,
                    items = listOf(
                        OrderItemRequest(productId = productId, quantity = 1)
                    )
                )
                val orderResponse2 = restTemplate.postForEntity(url("/orders"), orderRequest2, CreateOrderResponse::class.java)
                val orderId2 = orderResponse2.body?.orderId!!

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
