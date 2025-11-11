package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.shipping.dto.UpdateShippingStatusCommand
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.InvalidStatusTransitionException
import com.hhplus.ecommerce.common.exception.ShippingNotFoundException
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import com.hhplus.ecommerce.domain.shipping.repository.ShippingJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.LocalDateTime
import java.util.UUID

class ShippingServiceIntegrationTest (
    private val shippingRepository: ShippingJpaRepository,
    private val shippingService: ShippingService,
    private val userService: UserService,
    private val productService: ProductService,
    private val orderService: OrderService

): DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testUserId: UUID
    private lateinit var testOrderId: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID

    init {
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

    describe("getShipping") {
        context("저장된 배송 정보를 조회할 때") {
            it("정상적으로 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()

                val shipping = Shipping(
                    orderId = testOrderId,
                    trackingNumber = "TRACK001",
                    carrier = "CJ대한통운",
                    shippingStartAt = null,
                    estimatedArrivalAt = now.plusDays(3),
                    deliveredAt = null,
                    status = ShippingStatus.PENDING,
                    isDelayed = false,
                    isExpired = false,
                )
                shippingRepository.save(shipping)

                    // When
                    val result = shippingService.getShipping(testOrderId)

                    // Then
                    result.id shouldBe 1L
                    result.orderId shouldBe testOrderId
                    result.trackingNumber shouldBe "TRACK001"
                    result.carrier shouldBe "CJ대한통운"
                    result.status shouldBe ShippingStatus.PENDING
                }

                it("존재하지 않는 주문 ID로 조회하면 OrderNotFoundForShippingException을 발생시킨다") {
                    // When & Then
                    assertThatThrownBy {
                        shippingService.getShipping(testOrderId)
                    }.hasMessageContaining("Order not found with id: $testOrderId")
                }
            }
        }

        describe("updateShippingStatus") {
            context("배송 상태를 변경할 때") {
                it("PENDING에서 IN_TRANSIT으로 변경한다") {
                    // Given
                    val now = LocalDateTime.now()
                    val shipping = createShipping(testOrderId, ShippingStatus.PENDING, now)
                    val savedShipping = shippingRepository.save(shipping)

                    val command = UpdateShippingStatusCommand(
                        status = "IN_TRANSIT",
                        deliveredAt = null
                    )

                    // When
                    val result = shippingService.updateShippingStatus(savedShipping.id!!, command)

                    // Then
                    result.status shouldBe "IN_TRANSIT"
                    result.deliveredAt shouldBe null

                    // Repository에서 확인
                    val updated = shippingRepository.findById(savedShipping.id!!)
                        .orElseThrow { throw ShippingNotFoundException(savedShipping.id!!) }
                    updated.status shouldBe ShippingStatus.IN_TRANSIT
                }

                it("IN_TRANSIT에서 DELIVERED로 변경하고 지연 여부를 계산한다") {
                    // Given
                    val now = LocalDateTime.now()
                    val estimatedArrivalAt = now.plusDays(3)
                    val shipping = createShipping(testOrderId, ShippingStatus.IN_TRANSIT, now)

                    shipping.updateStatus(ShippingStatus.DELIVERED, now)
                    shippingRepository.save(shipping)

                    val deliveredAt = estimatedArrivalAt.plusDays(2) // 예상보다 2일 늦게 도착
                    val command = UpdateShippingStatusCommand(
                        status = "DELIVERED",
                        deliveredAt = deliveredAt
                    )

                    // When
                    val result = shippingService.updateShippingStatus(shipping.id!!, command)

                    // Then
                    result.status shouldBe "DELIVERED"
                    result.deliveredAt shouldBe deliveredAt

                    // Repository에서 확인
                    val updated = shippingRepository.findById(shipping.id!!)
                        .orElseThrow { throw ShippingNotFoundException(shipping.id!!) }
                    updated!!.status shouldBe ShippingStatus.DELIVERED
                    updated.deliveredAt shouldBe deliveredAt
                    updated.isDelayed shouldBe true
                }

                it("IN_TRANSIT에서 DELIVERED로 변경하고 정시 배송을 확인한다") {
                    // Given
                    val now = LocalDateTime.now()
                    val estimatedArrivalAt = now.plusDays(3)
                    val shipping = createShipping(testOrderId, ShippingStatus.IN_TRANSIT, now)

                    shipping.updateStatus(ShippingStatus.DELIVERED, now)
                    shippingRepository.save(shipping)

                    val deliveredAt = estimatedArrivalAt.minusHours(1) // 예상보다 1시간 일찍 도착
                    val command = UpdateShippingStatusCommand(
                        status = "DELIVERED",
                        deliveredAt = deliveredAt
                    )

                    // When
                    val result = shippingService.updateShippingStatus(shipping.id!!, command)

                    // Then
                    result.status shouldBe "DELIVERED"
                    result.deliveredAt shouldBe deliveredAt

                    // Repository에서 확인
                    val updated = shippingRepository.findById(shipping.id!!)
                        .orElseThrow { throw ShippingNotFoundException(shipping.id!!) }
                    updated!!.isDelayed shouldBe false // 정시 배송
                }

                it("PENDING에서 DELIVERED로 직접 변경하면 InvalidStatusTransitionException을 발생시킨다") {
                    // Given
                    val now = LocalDateTime.now()
                    val shipping = createShipping(testOrderId, ShippingStatus.PENDING, now)
                    shippingRepository.save(shipping)

                    val command = UpdateShippingStatusCommand(
                        status = "DELIVERED",
                        deliveredAt = now
                    )

                    // When & Then
                    assertThatThrownBy {
                        shippingService.updateShippingStatus(shipping.id!!, command)
                    }.isInstanceOf(InvalidStatusTransitionException::class.java)
                        .hasMessageContaining("Cannot transition from PENDING to DELIVERED")
                }
            }
        }
    }
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
                isExpired = false,
            )
        }
    }
}
