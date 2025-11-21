package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.ExpiredCouponException
import com.hhplus.ecommerce.common.exception.ForbiddenException
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.common.exception.InvalidQuantityException
import com.hhplus.ecommerce.common.exception.OrderNotFoundException
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class OrderServiceUnitTest : DescribeSpec({

    lateinit var orderRepository: OrderJpaRepository
    lateinit var productService: ProductService
    lateinit var couponService: CouponService
    lateinit var userService: UserService
    lateinit var orderService: OrderServiceImpl
    lateinit var cartService: CartService
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    beforeEach {
        orderRepository = mockk(relaxed = true)
        productService = mockk(relaxed = true)
        couponService = mockk(relaxed = true)
        userService = mockk(relaxed = true)
        cartService = mockk(relaxed = true)
        applicationEventPublisher = mockk(relaxed = true)

        orderService = OrderServiceImpl(
            orderRepository = orderRepository,
            productService = productService,
            couponService = couponService,
            userService = userService,
            cartService = cartService,
            applicationEventPublisher = applicationEventPublisher
        )
    }

    describe("createOrder - 주문 생성") {

        context("정상적인 주문 생성") {
            it("쿠폰 없이 주문을 생성할 수 있다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val quantity = 2
                val unitPrice = 10000L

                val user = createUser(id = userId, balance = 100000L)
                val product = createProduct(id= productId, name = "노트북", price = unitPrice, stock = 10, category = ProductCategory.ELECTRONICS)

                val command = CreateOrderCommand(
                    userId = user.id!!,
                    items = listOf(OrderItemCommand(product.id!!, quantity)),
                    couponId = null
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(product.id!!) } returns product
                every { productService.findAllByIdWithLock(listOf(product.id!!)) } returns listOf(product)
                every { productService.updateProduct(any()) } returns product

                val orderSlot = slot<Order>()
                every { orderRepository.save(capture(orderSlot)) } answers {
                    val order = orderSlot.captured
                    val idField = order.javaClass.superclass.getDeclaredField("id")
                    val createdAtField = order.javaClass.superclass.getDeclaredField("createdAt")

                    idField.isAccessible = true
                    idField.set(order, UUID.randomUUID())

                    createdAtField.isAccessible = true
                    createdAtField.set(order, LocalDateTime.now())

                    if (order.items.isEmpty()) {
                        order.items.add(
                            createOrderItem(
                                userId = user.id!!,
                                productId = product.id!!,
                                productName = product.name,
                                quantity = 1,
                                unitPrice = product.price,
                                subTotal = product.price * 1,
                                order = order
                            )
                        )
                    }

                    order.items.forEach { item ->
                        val idField = item.javaClass.superclass.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.set(item, UUID.randomUUID())
                    }

                    order
                }

                // when
                val response = orderService.createOrder(command)

                // then
                response.orderId shouldNotBe null
                response.userId shouldBe userId
                response.orderNumber shouldNotBe null
                response.items.size shouldBe 1
                response.items[0].productId shouldBe productId
                response.items[0].quantity shouldBe quantity
                response.pricing.totalAmount shouldBe 20000L
                response.pricing.discountAmount shouldBe 0L
                response.pricing.finalAmount shouldBe 20000L
                response.pricing.appliedCoupon shouldBe null
                response.status shouldBe "PENDING"
            }

            it("쿠폰을 사용하여 주문을 생성할 수 있다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val couponId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()

                val quantity = 1
                val unitPrice = 100000L
                val discountRate = 10

                val user = createUser(id = userId, balance = 100000L)
                val product = createProduct(id = productId, name = "노트북", price = unitPrice, stock = 10, category = ProductCategory.ELECTRONICS)
                val coupon = createCoupon(id = couponId, name = "10% 할인 쿠폰", discountRate = discountRate, totalQuantity = 100, issuedQuantity = 50)
                val userCoupon = createUserCoupon(id = userCouponId,userId = userId, couponId = couponId, status = CouponStatus.AVAILABLE)

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, quantity)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { productService.findAllByIdWithLock(listOf(product.id!!)) } returns listOf(product)
                every { couponService.findUserCoupon(userId, couponId) } returns userCoupon
                every { couponService.findCouponById(couponId) } returns coupon

                val orderSlot = slot<Order>()
                every { orderRepository.save(capture(orderSlot)) } answers {
                    val order = orderSlot.captured
                    val idField = order.javaClass.superclass.getDeclaredField("id")
                    val createdAtField = order.javaClass.superclass.getDeclaredField("createdAt")

                    idField.isAccessible = true
                    idField.set(order, UUID.randomUUID())

                    createdAtField.isAccessible = true
                    createdAtField.set(order, LocalDateTime.now())

                    if (order.items.isEmpty()) {
                        order.items.add(
                            createOrderItem(
                                userId = user.id!!,
                                productId = product.id!!,
                                productName = product.name,
                                quantity = 1,
                                unitPrice = product.price,
                                subTotal = product.price * 1,
                                order = order
                            )
                        )
                    }

                    order.items.forEach { item ->
                        val idField = item.javaClass.superclass.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.set(item, UUID.randomUUID())
                    }

                    order
                }

                // when
                val response = orderService.createOrder(command)

                // then
                response.pricing.totalAmount shouldBe 100000L
                response.pricing.discountAmount shouldBe 10000L // 10% 할인
                response.pricing.finalAmount shouldBe 90000L
                response.pricing.appliedCoupon shouldNotBe null
                response.pricing.appliedCoupon?.couponId shouldBe couponId
                response.pricing.appliedCoupon?.discountRate shouldBe discountRate
            }
        }

        context("주문 생성 실패 - 검증 오류") {
            it("수량이 0 이하이면 예외가 발생한다") {
                // given
                val command = CreateOrderCommand(
                    userId = UUID.randomUUID(),
                    items = listOf(OrderItemCommand(UUID.randomUUID(), 0)),
                    couponId = null
                )

                // when & then
                shouldThrow<InvalidQuantityException> {
                    orderService.createOrder(command)
                }
            }

            it("존재하지 않는 사용자는 주문할 수 없다") {
                // given
                val userId = UUID.randomUUID()
                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(UUID.randomUUID(), 1)),
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
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = null
                )

                val user = createUser(id = userId, balance = 100000L)
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
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 10)),
                    couponId = null
                )

                val user = createUser(id = userId, balance = 100000L)
                val product = createProduct(id = productId, name = "노트북", price = 100000L, stock = 5, category = ProductCategory.ELECTRONICS) // 재고 5개만 있음

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { productService.findAllByIdWithLock(any()) } returns listOf(product)

                // when & then
                shouldThrow<InsufficientStockException> {
                    orderService.createOrder(command)
                }
            }
        }

        context("주문 생성 실패 - 쿠폰 오류") {
            it("존재하지 않는 쿠폰은 사용할 수 없다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val couponId = UUID.randomUUID()

                val user = createUser(id = userId, balance = 100000L)
                val product = createProduct(id = productId, name = "노트북", price = 100000L, stock = 10, category = ProductCategory.ELECTRONICS)

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findAllByIdWithLock(any()) } returns listOf(product)
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
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val couponId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()

                val user = createUser(id = userId, balance = 100000L)
                val product = createProduct(id = productId, name = "노트북", price = 100000L, stock = 10, category = ProductCategory.ELECTRONICS)
                val userCoupon = createUserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.USED,
                    usedAt = LocalDateTime.now()
                )

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { productService.findAllByIdWithLock(any()) } returns listOf(product)
                every { couponService.findUserCoupon(userId, couponId) } returns userCoupon

                // when & then
                shouldThrow<InvalidCouponException> {
                    orderService.createOrder(command)
                }
            }

            it("만료된 쿠폰은 사용할 수 없다") {
                // given
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()
                val couponId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()

                val user = createUser(id = userId, balance = 100000L)
                val product = createProduct(id = productId, name = "노트북", price = 100000L, stock = 10, category = ProductCategory.ELECTRONICS)
                val userCoupon = createUserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = LocalDateTime.now().minusDays(60),
                    expiresAt = LocalDateTime.now().minusDays(1) // 만료됨
                )

                val command = CreateOrderCommand(
                    userId = userId,
                    items = listOf(OrderItemCommand(productId, 1)),
                    couponId = couponId
                )

                every { userService.getUser(userId) } returns user
                every { productService.findProductById(productId) } returns product
                every { productService.findAllByIdWithLock(any()) } returns listOf(product)
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
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val product = createProduct(id = productId, name = "노트북", price = 100000L, stock = 5, category = ProductCategory.ELECTRONICS)

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-20251103-001001",
                    totalAmount = 200000L,
                    discountAmount = 0L,
                    finalAmount = 200000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                val orderItem = OrderItem(
                    productId = productId,
                    userId = userId,
                    order = order,
                    productName = "노트북",
                    quantity = 2,
                    unitPrice = 100000L,
                    subtotal = 200000L
                )
                order.items.add(orderItem)

                val idField = order.javaClass.superclass.getDeclaredField("id")
                val updatedAtField = order.javaClass.superclass.getDeclaredField("updatedAt")

                idField.isAccessible = true
                updatedAtField.isAccessible = true
                idField.set(order, orderId)
                updatedAtField.set(order, LocalDateTime.now())

                val command = CancelOrderCommand(userId)

                every { orderRepository.findById(orderId) } returns Optional.of(order)
                every { productService.findProductById(productId) } returns product
                every { productService.findAllByIdWithLock(any()) } returns listOf(product)
                every { productService.updateProduct(any()) } returns product
                every { orderRepository.save(any()) } returns order

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
                val orderId = UUID.randomUUID()
                val command = CancelOrderCommand(UUID.randomUUID())

                every { orderRepository.findById(orderId) } returns Optional.empty()

                // when & then
                shouldThrow<OrderNotFoundException> {
                    orderService.cancelOrder(orderId, command)
                }
            }

            it("다른 사용자의 주문은 취소할 수 없다") {
                // given
                val orderId = UUID.randomUUID()
                val ownerId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()
                val productId = UUID.randomUUID()

                val order = Order(
                    userId = ownerId,
                    orderNumber = "ORD-20251103-001001",
                    totalAmount = 100000L,
                    discountAmount = 0L,
                    finalAmount = 100000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                val orderItem = createOrderItem(
                    productId = productId,
                    userId = ownerId,
                    order = order,
                    productName = "노트북",
                    quantity = 1,
                    unitPrice = 100000L,
                    subTotal = 100000L
                )
                order.items.add(orderItem)

                val command = CancelOrderCommand(otherUserId)

                every { orderRepository.save(any()) } returns order
                every { orderRepository.findById(orderId) } returns Optional.of(order)

                // when & then
                shouldThrow<ForbiddenException> {
                    orderService.cancelOrder(orderId, command)
                }
            }
        }
    }
}) {
    companion object {
        fun createUser(id: UUID, balance: Long): User {
            val user = User(balance = balance)

            val idField = user.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(user, id)
            return user
        }

        fun createProduct(
            id: UUID,
            name: String,
            price: Long,
            stock: Int,
            category: ProductCategory,
            salesCount: Int = 0
        ): Product {
            val product = Product(
                name = name,
                description = "$name 상세 설명",
                price = price,
                stock = stock,
                category = category,
                specifications = emptyMap(),
                salesCount = salesCount
            )

            val idField = product.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(product, id)
            return product
        }

        fun createCoupon(
            id: UUID,
            name: String,
            discountRate: Int,
            totalQuantity: Int,
            issuedQuantity: Int,
            validityDays: Int = 30
        ): Coupon {
            val coupon = Coupon(
                name = name,
                description = "$name 설명",
                discountRate = discountRate,
                totalQuantity = totalQuantity,
                issuedQuantity = issuedQuantity,
                startDate = LocalDateTime.now(),
                endDate = LocalDateTime.now().plusDays(365),
                validityDays = validityDays
            )

            val idField = coupon.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(coupon, id)
            return coupon
        }

        fun createUserCoupon(
            id: UUID,
            userId: UUID,
            couponId: UUID,
            status: CouponStatus,
            issuedAt: LocalDateTime = LocalDateTime.now(),
            expiresAt: LocalDateTime = LocalDateTime.now().plusDays(30),
            usedAt: LocalDateTime? = null
        ): UserCoupon {
            val userCoupon = UserCoupon(
                userId = userId,
                couponId = couponId,
                status = status,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                usedAt = usedAt
            )

            val idField = userCoupon.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(userCoupon, id)
            return userCoupon
        }

        fun createOrderItem(
            productId: UUID,
            userId: UUID,
            order: Order,
            productName: String,
            quantity: Int,
            unitPrice: Long,
            subTotal: Long
        ): OrderItem {
            val orderItem = OrderItem(
                productId,
                userId,
                order,
                productName,
                quantity,
                unitPrice,
                subTotal
            )
            return orderItem
        }
    }
}
