package com.hhplus.ecommerce.presentation.shipping

import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.ShippingNotFoundException
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import com.hhplus.ecommerce.domain.shipping.repository.ShippingJpaRepository
import com.hhplus.ecommerce.presentation.shipping.dto.ShippingDetailResponse
import com.hhplus.ecommerce.presentation.shipping.dto.UpdateShippingStatusRequest
import com.hhplus.ecommerce.presentation.shipping.dto.UpdateShippingStatusResponse
import com.hhplus.ecommerce.presentation.shipping.dto.UserShippingListResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShippingE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val shippingRepository: ShippingJpaRepository,
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService
) : DescribeSpec({
    var testUserId: UUID? = null

    var product1Id: UUID? = null
    var product2Id: UUID? = null

    var testOrderId: UUID? = null
    var baseUrl = "http://localhost:$port/api/shippings"
    beforeSpec {
        val createUserCommand = CreateUserCommand(
            balance = 3000000L
        )

        val savedUser = userService.createUser(createUserCommand)
        testUserId = savedUser.id!!

        val product1 = Product(
            name = "노트북",
            description = "고성능 노트북",
            price = 100000L,
            stock = 10,
            category = ProductCategory.ELECTRONICS,
            specifications = emptyMap(),
            salesCount = 0
        )
        val savedProduct1 = productService.updateProduct(product1)
        product1Id = savedProduct1.id!!

        val product2 = Product(
            name = "마우스",
            description = "무선 마우스",
            price = 30000L,
            stock = 20,
            category = ProductCategory.ELECTRONICS,
            specifications = emptyMap(),
            salesCount = 0
        )
        val savedProduct2 = productService.updateProduct(product2)
        product2Id = savedProduct2.id!!

        val orderCommand = CreateOrderCommand(
            userId = testUserId,
            items = listOf(OrderItemCommand(product1Id, 2)),
            couponId = null
        )

        val createdOrder = orderService.createOrder(orderCommand)
        testOrderId = createdOrder.orderId
    }

    afterEach {
        shippingRepository.deleteAll()
    }

    describe("GET /shippings/{orderId}") {
        context("배송 정보 조회") {
            it("정상적으로 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()

                val shipping = createShipping(testOrderId!!, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                shippingRepository.save(shipping)

                // When
                val url = "$baseUrl/shippings/${testOrderId!!}"
                val response = restTemplate.getForEntity(url, ShippingDetailResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                (body!!.shippingId) shouldBe shipping.id
                body.status shouldBe "PENDING"
            }

            it("존재하지 않는 주문 ID로 조회하면 404를 반환한다") {
                // When
                val url = "$baseUrl/shippings/$testOrderId"
                val response = restTemplate.getForEntity(url, String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("PATCH /shippings/{shippingId}/status") {
        context("배송 상태 변경 - 정상 케이스") {
            it("PENDING에서 IN_TRANSIT으로 변경한다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(testOrderId!!, ShippingStatus.PENDING, now)
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.shippingId shouldBe shipping.id
                body.orderId shouldBe shipping.orderId
                body.status shouldBe "IN_TRANSIT"
                body.deliveredAt shouldBe null

                // DB에 실제로 저장되었는지 확인
                val updatedShipping = shippingRepository.findById(shipping.id!!)
                    .orElseThrow { throw ShippingNotFoundException(shipping.id!!) }
                updatedShipping shouldNotBe null
                updatedShipping!!.status shouldBe ShippingStatus.IN_TRANSIT
                updatedShipping.deliveredAt shouldBe null
            }

            it("IN_TRANSIT에서 DELIVERED로 변경하고 배송 완료 시간을 설정한다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(testOrderId!!, ShippingStatus.IN_TRANSIT, now)

                shipping.updateStatus(ShippingStatus.DELIVERED, now)
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt.plusDays(2) // 2일 지연
                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.shippingId shouldBe shipping.id
                body.orderId shouldBe shipping.orderId
                body.status shouldBe "DELIVERED"
                body.deliveredAt shouldBe deliveredAt

                // DB에 저장 확인: 지연 플래그가 true로 설정되어야 함
                val updatedShipping = shippingRepository.findById(shipping.id!!)
                    .orElseThrow { throw ShippingNotFoundException(shipping.id!!) }
                updatedShipping shouldNotBe null
                updatedShipping!!.status shouldBe ShippingStatus.DELIVERED
                updatedShipping.deliveredAt shouldBe deliveredAt
                updatedShipping.isDelayed shouldBe true // 2일 지연
            }

            it("배송 완료 시 예상 도착일보다 빨리 도착하면 지연 플래그가 false이다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(5)
                val shipping = createShipping(testOrderId!!, ShippingStatus.IN_TRANSIT, now)

                shipping.updateStatus(ShippingStatus.DELIVERED, now)
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt.minusDays(1) // 1일 빨리 도착
                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.shippingId shouldBe shipping.id
                body.status shouldBe "DELIVERED"
                body.deliveredAt shouldBe deliveredAt

                // DB에서 지연 플래그 확인
                val updatedShipping = shippingRepository.findById(shipping.id!!)
                    .orElseThrow { throw ShippingNotFoundException(shipping.id!!) }
                updatedShipping shouldNotBe null
                updatedShipping!!.status shouldBe ShippingStatus.DELIVERED
                updatedShipping.deliveredAt shouldBe deliveredAt
                updatedShipping.isDelayed shouldBe false
            }

            it("배송 완료 시 예상 도착일과 동일하면 지연 플래그가 false이다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(testOrderId!!, ShippingStatus.IN_TRANSIT, now)

                shipping.updateStatus(ShippingStatus.DELIVERED, now)
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt // 정확히 예상일에 도착
                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK

                // DB에서 지연 플래그 확인 (isAfter가 false이므로 지연 아님)
                val updatedShipping = shippingRepository.findById(shipping.id!!)
                    .orElseThrow { throw ShippingNotFoundException(shipping.id!!) }
                updatedShipping shouldNotBe null
                updatedShipping!!.isDelayed shouldBe false
            }
        }

        context("배송 상태 변경 - 예외 케이스") {
            it("잘못된 상태 전이를 시도하면 400을 반환한다 (PENDING -> DELIVERED)") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(testOrderId!!, ShippingStatus.PENDING, now)

                shipping.updateStatus(ShippingStatus.DELIVERED, now)
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = now
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST

                // DB 상태가 변경되지 않았는지 확인
                val unchangedShipping = shippingRepository.findById(shipping.id!!)
                    .orElseThrow{ throw ShippingNotFoundException(shipping.id!!) }

                unchangedShipping shouldNotBe null
                unchangedShipping!!.status shouldBe ShippingStatus.PENDING
            }

            it("존재하지 않는 배송 ID로 상태 변경을 시도하면 404를 반환한다") {
                // Given
                val nonExistentShippingId = 999999L
                val request = UpdateShippingStatusRequest(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                // When
                val url = "$baseUrl/shippings/$nonExistentShippingId/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
}) {
    companion object {
        private fun createShipping(
            orderId: UUID,
            status: ShippingStatus,
            createdAt: LocalDateTime
        ): Shipping {
            return Shipping(
                orderId = orderId,
                carrier = "CJ대한통운",
                trackingNumber = "TRACK${String.format("%03d")}",
                shippingStartAt = if (status != ShippingStatus.PENDING) createdAt else null,
                estimatedArrivalAt = createdAt.plusDays(3),
                deliveredAt = if (status == ShippingStatus.DELIVERED) createdAt.plusDays(3) else null,
                status = status,
                isDelayed = false,
                isExpired = false
            )
        }
    }
}
