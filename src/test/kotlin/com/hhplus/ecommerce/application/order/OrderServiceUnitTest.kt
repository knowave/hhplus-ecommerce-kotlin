package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
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
import com.hhplus.ecommerce.common.lock.LockManager
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.domain.order.OrderRepository
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.application.order.dto.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.reflection.beLateInit
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
    lateinit var productService: ProductService
    lateinit var couponService: CouponService
    lateinit var userService: UserService
    lateinit var orderService: OrderServiceImpl
    lateinit var cartService: CartService
    lateinit var lockManager: LockManager

    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        orderRepository = mockk(relaxed = true)
        productService = mockk(relaxed = true)
        couponService = mockk(relaxed = true)
        userService = mockk(relaxed = true)
        cartService = mockk(relaxed = true)
        lockManager = LockManager() // 실제 LockManager 인스턴스 사용

        orderService = OrderServiceImpl(
            orderRepository = orderRepository,
            productService = productService,
            couponService = couponService,
            userService = userService,
            cartService = cartService,
            lockManager = lockManager
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

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, quantity)),
                    couponId = null
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { productService.updateProduct(any()) } returns product
                every { orderRepository.generateId() } returns 1001L
                every { orderRepository.generateItemId() } returns 1L
                every { orderRepository.generateOrderNumber(1001L) } returns "ORD-20251103-001001"

                val orderSlot = slot<Order>()
                every { orderRepository.save(capture(orderSlot)) } answers { orderSlot.captured }

                // when
                val response = orderService.createOrder(command)

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
                verify(exactly = 1) { productService.updateProduct(any()) }
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

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, quantity)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { couponService.findUserCoupon(userId, couponId) } returns userCoupon
                every { couponService.findCouponById(couponId) } returns coupon
                every { orderRepository.generateId() } returns 1001L
                every { orderRepository.generateItemId() } returns 1L
                every { orderRepository.generateOrderNumber(1001L) } returns "ORD-20251103-001001"

                val orderSlot = slot<Order>()
                every { orderRepository.save(capture(orderSlot)) } answers { orderSlot.captured }

                // when
                val response = orderService.createOrder(command)

                // then
                response.pricing.totalAmount shouldBe 100000L
                response.pricing.discountAmount shouldBe 10000L // 10% 할인
                response.pricing.finalAmount shouldBe 90000L
                response.pricing.appliedCoupon shouldNotBe null
                response.pricing.appliedCoupon?.couponId shouldBe couponId
                response.pricing.appliedCoupon?.discountRate shouldBe discountRate

                // 쿠폰 사용 처리 검증
                verify(exactly = 1) { couponService.updateUserCoupon(any()) }
                userCoupon.status shouldBe CouponStatus.USED
                userCoupon.usedAt shouldNotBe null
            }
        }

        context("주문 생성 실패 - 검증 오류") {
            it("수량이 0 이하이면 예외가 발생한다") {
                // given
                val command = CreateOrderCommand(
                    userId = 100L,
                    items = listOf(OrderItemCommand(1L, 0)),
                    couponId = null
                )

                // when & then
                shouldThrow<InvalidQuantityException> {
                    orderService.createOrder(command)
                }
            }

            it("존재하지 않는 사용자는 주문할 수 없다") {
                // given
                val userId = 999L
                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(1L, 1)),
                    couponId = null
                )

                // userService.getUser가 UserNotFoundException을 던지도록 모킹
                every { userService.getUser(userId) } throws UserNotFoundException(userId)

                // when & then
                shouldThrow<UserNotFoundException> {
                    orderService.createOrder(command)
                }
            }

            it("존재하지 않는 상품은 주문할 수 없다") {
                // given
                val userId = 100L
                val productId = 999L
                val now = LocalDateTime.now().format(dateFormatter)
                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = null
                )

                val user = createUser(userId, 100000L, now, now)
                every { userService.getUser(userId) } returns user

                // productService.findProductById가 ProductNotFoundException을 던지도록 모킹
                every { productService.findProductById(productId) } throws ProductNotFoundException(productId)

                // when & then
                shouldThrow<ProductNotFoundException> {
                    orderService.createOrder(command)
                }
            }

            it("재고가 부족하면 주문할 수 없다") {
                // given
                val userId = 100L
                val productId = 1L
                val now = LocalDateTime.now().format(dateFormatter)
                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 10)),
                    couponId = null
                )

                val user = createUser(userId, 100000L, now, now)
                val product = createProduct(productId, "노트북", 100000L, 5, ProductCategory.ELECTRONICS, 0, now, now) // 재고 5개만 있음

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product

                // when & then
                shouldThrow<InsufficientStockException> {
                    orderService.createOrder(command)
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

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product

                // couponService.findUserCoupon이 CouponNotFoundException을 던지도록 모킹
                every { couponService.findUserCoupon(userId, couponId) } throws CouponNotFoundException(couponId)

                // when & then
                shouldThrow<CouponNotFoundException> {
                    orderService.createOrder(command)
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

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { couponService.findUserCoupon(userId, couponId) } returns userCoupon

                // when & then
                shouldThrow<InvalidCouponException> {
                    orderService.createOrder(command)
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

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { couponService.findUserCoupon(userId, couponId) } returns userCoupon

                // when & then
                shouldThrow<ExpiredCouponException> {
                    orderService.createOrder(command)
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

                val command = CancelOrderCommand(userId)

                every { orderRepository.findById(orderId) } returns order
                every { productService.findProductById(productId) } returns product
                every { productService.updateProduct(any()) } returns product

                // when
                val response = orderService.cancelOrder(orderId, command)

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
                val command = CancelOrderCommand(100L)

                every { orderRepository.findById(orderId) } returns null

                // when & then
                shouldThrow<OrderNotFoundException> {
                    orderService.cancelOrder(orderId, command)
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

                val command = CancelOrderCommand(otherUserId)

                every { orderRepository.findById(orderId) } returns order

                // when & then
                shouldThrow<ForbiddenException> {
                    orderService.cancelOrder(orderId, command)
                }
            }
        }
    }
}) {
    companion object {
        fun createUser(
            userId: Long,
            balance: Long,
            createdAt: String,
            updatedAt: String
        ): User {
            return User(
                id = userId,
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