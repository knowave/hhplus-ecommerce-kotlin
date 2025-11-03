package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.common.exception.CannotCancelOrderException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.ExpiredCouponException
import com.hhplus.ecommerce.common.exception.ForbiddenException
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.common.exception.InvalidOrderItemsException
import com.hhplus.ecommerce.common.exception.InvalidQuantityException
import com.hhplus.ecommerce.common.exception.OrderNotFoundException
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponStatus
import com.hhplus.ecommerce.infrastructure.order.OrderRepository
import com.hhplus.ecommerce.infrastructure.product.ProductCategory
import com.hhplus.ecommerce.infrastructure.product.ProductRepository
import com.hhplus.ecommerce.infrastructure.user.UserRepository
import com.hhplus.ecommerce.model.coupon.Coupon
import com.hhplus.ecommerce.model.coupon.UserCoupon
import com.hhplus.ecommerce.model.order.Order
import com.hhplus.ecommerce.model.order.OrderItem
import com.hhplus.ecommerce.model.order.OrderStatus
import com.hhplus.ecommerce.model.product.Product
import com.hhplus.ecommerce.model.user.User
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.OrderItemRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OrderServiceUnitTest : DescribeSpec({

    lateinit var orderRepository: OrderRepository
    lateinit var productRepository: ProductRepository
    lateinit var couponRepository: CouponRepository
    lateinit var userRepository: UserRepository
    lateinit var orderService: OrderServiceImpl

    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        orderRepository = mockk(relaxed = true)
        productRepository = mockk(relaxed = true)
        couponRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)

        orderService = OrderServiceImpl(
            orderRepository = orderRepository,
            productRepository = productRepository,
            couponRepository = couponRepository,
            userRepository = userRepository
        )
    }

    describe("createOrder - 주문 생성") {

        context("정상적인 주문 생성") {
            it("쿠폰 없이 주문을 생성할 수 있다") {
                // given
                val userId = 100L
                val productId = 1L
                val quantity = 2
                val unitPrice = 10000L
                val now = LocalDateTime.now().format(dateFormatter)

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", unitPrice, 10, ProductCategory.ELECTRONICS, 0, now, now)

                val orderItemRequest = OrderItemRequest(productId, quantity)
                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(orderItemRequest),
                    couponId = null
                )

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { orderRepository.generateId() } returns 1001L
                every { orderRepository.generateItemId() } returns 1L
                every { orderRepository.generateOrderNumber(1001L) } returns "ORD-20251103-001001"

                val orderSlot = slot<Order>()
                every { orderRepository.save(capture(orderSlot)) } answers { orderSlot.captured }

                // when
                val response = orderService.createOrder(request)

                // then
                response.orderId shouldBe 1001L
                response.userId shouldBe userId
                response.orderNumber shouldBe "ORD-20251103-001001"
                response.items.size shouldBe 1
                response.items[0].productId shouldBe productId
                response.items[0].quantity shouldBe quantity
                response.pricing.totalAmount shouldBe 20000L
                response.pricing.discountAmount shouldBe 0L
                response.pricing.finalAmount shouldBe 20000L
                response.pricing.appliedCoupon shouldBe null
                response.status shouldBe "PENDING"

                // 재고 차감 검증
                verify(exactly = 1) { productRepository.save(any()) }
                product.stock shouldBe 8 // 10 - 2 = 8
            }

            it("쿠폰을 사용하여 주문을 생성할 수 있다") {
                // given
                val userId = 100L
                val productId = 1L
                val couponId = 10L
                val quantity = 1
                val unitPrice = 100000L
                val discountRate = 10
                val now = LocalDateTime.now().format(dateFormatter)

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", unitPrice, 10, ProductCategory.ELECTRONICS, 0, now, now)
                val coupon = createCoupon(couponId, "10% 할인 쿠폰", discountRate, 100, 50, "2025-01-01", "2025-12-31", 30, now)
                val expiresAt = LocalDateTime.now().plusDays(30).format(dateFormatter)
                val userCoupon = createUserCoupon(1L, userId, couponId, CouponStatus.AVAILABLE, now, expiresAt, null)

                val orderItemRequest = OrderItemRequest(productId, quantity)
                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(orderItemRequest),
                    couponId = couponId
                )

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { couponRepository.findUserCoupon(userId, couponId) } returns userCoupon
                every { couponRepository.findById(couponId) } returns coupon
                every { orderRepository.generateId() } returns 1001L
                every { orderRepository.generateItemId() } returns 1L
                every { orderRepository.generateOrderNumber(1001L) } returns "ORD-20251103-001001"

                val orderSlot = slot<Order>()
                every { orderRepository.save(capture(orderSlot)) } answers { orderSlot.captured }

                // when
                val response = orderService.createOrder(request)

                // then
                response.pricing.totalAmount shouldBe 100000L
                response.pricing.discountAmount shouldBe 10000L // 10% 할인
                response.pricing.finalAmount shouldBe 90000L
                response.pricing.appliedCoupon shouldNotBe null
                response.pricing.appliedCoupon!!.couponId shouldBe couponId
                response.pricing.appliedCoupon!!.discountRate shouldBe discountRate

                // 쿠폰 사용 처리 검증
                verify(exactly = 1) { couponRepository.saveUserCoupon(any()) }
                userCoupon.status shouldBe CouponStatus.USED
                userCoupon.usedAt shouldNotBe null
            }
        }

        context("주문 생성 실패 - 검증 오류") {
            it("주문 상품 목록이 비어있으면 예외가 발생한다") {
                // given
                val request = CreateOrderRequest(
                    userId = 100L,
                    items = emptyList(),
                    couponId = null
                )

                // when & then
                shouldThrow<InvalidOrderItemsException> {
                    orderService.createOrder(request)
                }
            }

            it("수량이 0 이하이면 예외가 발생한다") {
                // given
                val request = CreateOrderRequest(
                    userId = 100L,
                    items = listOf(OrderItemRequest(1L, 0)),
                    couponId = null
                )

                // when & then
                shouldThrow<InvalidQuantityException> {
                    orderService.createOrder(request)
                }
            }

            it("존재하지 않는 사용자는 주문할 수 없다") {
                // given
                val request = CreateOrderRequest(
                    userId = 999L,
                    items = listOf(OrderItemRequest(1L, 1)),
                    couponId = null
                )

                every { userRepository.findById(999L) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    orderService.createOrder(request)
                }
            }

            it("존재하지 않는 상품은 주문할 수 없다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now().format(dateFormatter)
                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(OrderItemRequest(999L, 1)),
                    couponId = null
                )

                val user = createUser(userId, 100000L, now, now)
                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(999L) } returns null

                // when & then
                shouldThrow<ProductNotFoundException> {
                    orderService.createOrder(request)
                }
            }

            it("재고가 부족하면 주문할 수 없다") {
                // given
                val userId = 100L
                val productId = 1L
                val now = LocalDateTime.now().format(dateFormatter)
                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(OrderItemRequest(productId, 10)),
                    couponId = null
                )

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", 100000L, 5, ProductCategory.ELECTRONICS, 0, now, now) // 재고 5개만 있음

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product

                // when & then
                shouldThrow<InsufficientStockException> {
                    orderService.createOrder(request)
                }
            }
        }

        context("주문 생성 실패 - 쿠폰 오류") {
            it("존재하지 않는 쿠폰은 사용할 수 없다") {
                // given
                val userId = 100L
                val productId = 1L
                val couponId = 999L
                val now = LocalDateTime.now().format(dateFormatter)

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", 100000L, 10, ProductCategory.ELECTRONICS, 0, now, now)

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(OrderItemRequest(productId, 1)),
                    couponId = couponId
                )

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                shouldThrow<CouponNotFoundException> {
                    orderService.createOrder(request)
                }
            }

            it("이미 사용한 쿠폰은 재사용할 수 없다") {
                // given
                val userId = 100L
                val productId = 1L
                val couponId = 10L
                val now = LocalDateTime.now().format(dateFormatter)

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", 100000L, 10, ProductCategory.ELECTRONICS, 0, now, now)
                val userCoupon = createUserCoupon(
                    1L, userId, couponId, CouponStatus.USED,
                    now, LocalDateTime.now().plusDays(30).format(dateFormatter), now
                )

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(OrderItemRequest(productId, 1)),
                    couponId = couponId
                )

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { couponRepository.findUserCoupon(userId, couponId) } returns userCoupon

                // when & then
                shouldThrow<InvalidCouponException> {
                    orderService.createOrder(request)
                }
            }

            it("만료된 쿠폰은 사용할 수 없다") {
                // given
                val userId = 100L
                val productId = 1L
                val couponId = 10L
                val now = LocalDateTime.now().format(dateFormatter)

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", 100000L, 10, ProductCategory.ELECTRONICS, 0, now, now)
                val userCoupon = createUserCoupon(
                    1L, userId, couponId, CouponStatus.AVAILABLE,
                    LocalDateTime.now().minusDays(60).format(dateFormatter),
                    LocalDateTime.now().minusDays(1).format(dateFormatter), // 만료됨
                    null
                )

                val request = CreateOrderRequest(
                    userId = userId,
                    items = listOf(OrderItemRequest(productId, 1)),
                    couponId = couponId
                )

                every { userRepository.findById(userId) } returns user
                every { productRepository.findById(productId) } returns product
                every { couponRepository.findUserCoupon(userId, couponId) } returns userCoupon

                // when & then
                shouldThrow<ExpiredCouponException> {
                    orderService.createOrder(request)
                }
            }
        }
    }

    describe("getOrderDetail - 주문 상세 조회") {
        context("정상적인 조회") {
            it("주문 상세 정보를 조회할 수 있다") {
                // given
                val orderId = 1001L
                val userId = 100L
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(1L, 1L, orderId, "노트북", 1, 100000L, 100000L)
                )
                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-20251103-001001",
                    items = orderItems,
                    totalAmount = 100000L,
                    discountAmount = 0L,
                    finalAmount = 100000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                every { orderRepository.findById(orderId) } returns order

                // when
                val response = orderService.getOrderDetail(orderId, userId)

                // then
                response.orderId shouldBe orderId
                response.userId shouldBe userId
                response.orderNumber shouldBe "ORD-20251103-001001"
                response.status shouldBe "PENDING"
                response.pricing.totalAmount shouldBe 100000L
                response.payment shouldBe null
            }
        }

        context("조회 실패") {
            it("존재하지 않는 주문은 조회할 수 없다") {
                // given
                val orderId = 999L
                val userId = 100L

                every { orderRepository.findById(orderId) } returns null

                // when & then
                shouldThrow<OrderNotFoundException> {
                    orderService.getOrderDetail(orderId, userId)
                }
            }

            it("다른 사용자의 주문은 조회할 수 없다") {
                // given
                val orderId = 1001L
                val ownerId = 100L
                val otherUserId = 200L
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(1L, 1L, orderId, "노트북", 1, 100000L, 100000L)
                )
                val order = Order(
                    id = orderId,
                    userId = ownerId,
                    orderNumber = "ORD-20251103-001001",
                    items = orderItems,
                    totalAmount = 100000L,
                    discountAmount = 0L,
                    finalAmount = 100000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                every { orderRepository.findById(orderId) } returns order

                // when & then
                shouldThrow<ForbiddenException> {
                    orderService.getOrderDetail(orderId, otherUserId)
                }
            }
        }
    }

    describe("cancelOrder - 주문 취소") {
        context("정상적인 취소") {
            it("PENDING 상태의 주문을 취소할 수 있다") {
                // given
                val orderId = 1001L
                val userId = 100L
                val productId = 1L
                val now = LocalDateTime.now()
                val nowStr = now.format(dateFormatter)

                val product = createProduct(productId, "노트북", 100000L, 5, ProductCategory.ELECTRONICS, 0, nowStr, nowStr)
                val orderItems = listOf(
                    OrderItem(1L, productId, orderId, "노트북", 2, 100000L, 200000L)
                )
                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-20251103-001001",
                    items = orderItems,
                    totalAmount = 200000L,
                    discountAmount = 0L,
                    finalAmount = 200000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val request = CancelOrderRequest(userId)

                every { orderRepository.findById(orderId) } returns order
                every { productRepository.findById(productId) } returns product

                // when
                val response = orderService.cancelOrder(orderId, request)

                // then
                response.orderId shouldBe orderId
                response.status shouldBe "CANCELLED"
                response.refund.restoredStock.size shouldBe 1
                response.refund.restoredStock[0].productId shouldBe productId
                response.refund.restoredStock[0].quantity shouldBe 2

                // 재고 복원 검증
                product.stock shouldBe 7 // 5 + 2 = 7
                verify(exactly = 1) { orderRepository.save(any()) }
            }
        }

        context("취소 실패") {
            it("존재하지 않는 주문은 취소할 수 없다") {
                // given
                val orderId = 999L
                val request = CancelOrderRequest(100L)

                every { orderRepository.findById(orderId) } returns null

                // when & then
                shouldThrow<OrderNotFoundException> {
                    orderService.cancelOrder(orderId, request)
                }
            }

            it("다른 사용자의 주문은 취소할 수 없다") {
                // given
                val orderId = 1001L
                val ownerId = 100L
                val otherUserId = 200L
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(1L, 1L, orderId, "노트북", 1, 100000L, 100000L)
                )
                val order = Order(
                    id = orderId,
                    userId = ownerId,
                    orderNumber = "ORD-20251103-001001",
                    items = orderItems,
                    totalAmount = 100000L,
                    discountAmount = 0L,
                    finalAmount = 100000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val request = CancelOrderRequest(otherUserId)

                every { orderRepository.findById(orderId) } returns order

                // when & then
                shouldThrow<ForbiddenException> {
                    orderService.cancelOrder(orderId, request)
                }
            }

            it("PAID 상태의 주문은 취소할 수 없다") {
                // given
                val orderId = 1001L
                val userId = 100L
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(1L, 1L, orderId, "노트북", 1, 100000L, 100000L)
                )
                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-20251103-001001",
                    items = orderItems,
                    totalAmount = 100000L,
                    discountAmount = 0L,
                    finalAmount = 100000L,
                    appliedCouponId = null,
                    status = OrderStatus.PAID,
                    createdAt = now,
                    updatedAt = now
                )

                val request = CancelOrderRequest(userId)

                every { orderRepository.findById(orderId) } returns order

                // when & then
                shouldThrow<CannotCancelOrderException> {
                    orderService.cancelOrder(orderId, request)
                }
            }
        }
    }
}) {
    companion object {
        fun createUser(
            id: Long,
            balance: Long,
            createdAt: String,
            updatedAt: String
        ): User {
            return User(
                id = id,
                balance = balance,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }

        fun createProduct(
            id: Long,
            name: String,
            price: Long,
            stock: Int,
            category: ProductCategory,
            salesCount: Int,
            createdAt: String,
            updatedAt: String
        ): Product {
            return Product(
                id = id,
                name = name,
                description = "$name 상세 설명",
                price = price,
                stock = stock,
                category = category,
                specifications = emptyMap(),
                salesCount = salesCount,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }

        fun createCoupon(
            id: Long,
            name: String,
            discountRate: Int,
            totalQuantity: Int,
            issuedQuantity: Int,
            startDate: String,
            endDate: String,
            validityDays: Int,
            createdAt: String
        ): Coupon {
            return Coupon(
                id = id,
                name = name,
                description = "$name 설명",
                discountRate = discountRate,
                totalQuantity = totalQuantity,
                issuedQuantity = issuedQuantity,
                startDate = startDate,
                endDate = endDate,
                validityDays = validityDays,
                createdAt = createdAt
            )
        }

        fun createUserCoupon(
            id: Long,
            userId: Long,
            couponId: Long,
            status: CouponStatus,
            issuedAt: String,
            expiresAt: String,
            usedAt: String?
        ): UserCoupon {
            return UserCoupon(
                id = id,
                userId = userId,
                couponId = couponId,
                status = status,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                usedAt = usedAt
            )
        }
    }
}
