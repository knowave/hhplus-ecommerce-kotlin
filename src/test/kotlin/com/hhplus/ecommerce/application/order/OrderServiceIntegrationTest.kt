package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.coupon.CouponServiceImpl
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.product.ProductServiceImpl
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.UserServiceImpl
import com.hhplus.ecommerce.common.exception.CannotCancelOrderException
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.common.exception.OrderNotFoundException
import com.hhplus.ecommerce.common.lock.LockManager
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepositoryImpl
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.domain.order.OrderRepository
import com.hhplus.ecommerce.infrastructure.order.OrderRepositoryImpl
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepositoryImpl
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.infrastructure.user.UserRepositoryImpl
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.OrderItemRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OrderServiceIntegrationTest : DescribeSpec({

    lateinit var orderRepository: OrderRepository
    lateinit var productRepository: ProductRepository
    lateinit var couponRepository: CouponRepository
    lateinit var userRepository: UserRepository
    lateinit var productService: ProductService
    lateinit var couponService: CouponService
    lateinit var userService: UserService
    lateinit var orderService: OrderService
    lateinit var lockManager: LockManager

    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // ID 카운터
    var userIdCounter = 1000L
    var productIdCounter = 1000L
    var couponIdCounter = 1000L

    beforeEach {
        // 실제 구현체 사용
        orderRepository = OrderRepositoryImpl()
        productRepository = ProductRepositoryImpl()
        couponRepository = CouponRepositoryImpl()
        userRepository = UserRepositoryImpl()
        lockManager = LockManager()

        productService = ProductServiceImpl(productRepository)
        couponService = CouponServiceImpl(couponRepository, lockManager)
        userService = UserServiceImpl(userRepository)

        orderService = OrderServiceImpl(
            orderRepository = orderRepository,
            productService = productService,
            couponService = couponService,
            userService = userService,
            lockManager = com.hhplus.ecommerce.common.lock.LockManager()
        )

        // 카운터 초기화
        userIdCounter = 1000L
        productIdCounter = 1000L
        couponIdCounter = 1000L
    }

    fun generateUserId(): Long = ++userIdCounter
    fun generateProductId(): Long = ++productIdCounter
    fun generateCouponId(): Long = ++couponIdCounter

    describe("OrderService 통합 테스트 - 주문 전체 플로우") {

        context("주문 생성 및 조회 통합 시나리오") {
            it("사용자가 상품을 주문하고 조회할 수 있다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 100000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // when - 주문 생성
                val request = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 2)),
                    couponId = null
                )
                val orderResponse = orderService.createOrder(request)

                // then - 주문 생성 검증
                orderResponse.orderId shouldNotBe null
                orderResponse.userId shouldBe user.id
                orderResponse.items.size shouldBe 1
                orderResponse.pricing.totalAmount shouldBe 100000L
                orderResponse.pricing.finalAmount shouldBe 100000L
                orderResponse.status shouldBe "PENDING"

                // when - 주문 조회
                val orderDetail = orderService.getOrderDetail(orderResponse.orderId, user.id)

                // then - 주문 상세 정보 검증
                orderDetail.orderId shouldBe orderResponse.orderId
                orderDetail.userId shouldBe user.id
                orderDetail.status shouldBe "PENDING"
                orderDetail.pricing.totalAmount shouldBe 100000L

                // then - 재고 차감 검증
                val updatedProduct = productRepository.findById(product.id)
                updatedProduct!!.stock shouldBe 8 // 10 - 2 = 8
            }

            it("쿠폰을 사용하여 주문할 수 있다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 100000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 100000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // given - 쿠폰 생성
                val coupon = Coupon(
                    id = generateCouponId(),
                    name = "10% 할인 쿠폰",
                    description = "전 상품 10% 할인",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 1,
                    startDate = "2025-01-01",
                    endDate = "2025-12-31",
                    validityDays = 30,
                    createdAt = now
                )
                couponRepository.save(coupon)

                // given - 사용자 쿠폰 발급
                val userCoupon = UserCoupon(
                    id = couponRepository.generateUserCouponId(),
                    userId = user.id,
                    couponId = coupon.id,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now,
                    expiresAt = LocalDateTime.now().plusDays(30).format(dateFormatter),
                    usedAt = null
                )
                couponRepository.saveUserCoupon(userCoupon)

                // when - 쿠폰을 사용한 주문 생성
                val request = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 1)),
                    couponId = coupon.id
                )
                val orderResponse = orderService.createOrder(request)

                // then - 할인 적용 검증
                orderResponse.pricing.totalAmount shouldBe 100000L
                orderResponse.pricing.discountAmount shouldBe 10000L // 10% 할인
                orderResponse.pricing.finalAmount shouldBe 90000L
                orderResponse.pricing.appliedCoupon shouldNotBe null
                orderResponse.pricing.appliedCoupon!!.discountRate shouldBe 10

                // then - 쿠폰 사용 상태 검증
                val updatedUserCoupon = couponRepository.findUserCoupon(user.id, coupon.id)
                updatedUserCoupon!!.status shouldBe CouponStatus.USED
                updatedUserCoupon.usedAt shouldNotBe null
            }

            it("여러 상품을 함께 주문할 수 있다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 500000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 여러 상품 생성
                val product1 = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 100000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product1)

                val product2 = Product(
                    id = generateProductId(),
                    name = "마우스",
                    description = "무선 마우스",
                    price = 30000L,
                    stock = 20,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product2)

                val product3 = Product(
                    id = generateProductId(),
                    name = "키보드",
                    description = "기계식 키보드",
                    price = 50000L,
                    stock = 15,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product3)

                // when - 여러 상품 주문
                val request = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(
                        OrderItemRequest(product1.id, 1),
                        OrderItemRequest(product2.id, 2),
                        OrderItemRequest(product3.id, 1)
                    ),
                    couponId = null
                )
                val orderResponse = orderService.createOrder(request)

                // then - 주문 검증
                orderResponse.items.size shouldBe 3
                orderResponse.pricing.totalAmount shouldBe 210000L // 100000 + 60000 + 50000

                // then - 모든 상품의 재고 차감 검증
                productRepository.findById(product1.id)!!.stock shouldBe 9
                productRepository.findById(product2.id)!!.stock shouldBe 18
                productRepository.findById(product3.id)!!.stock shouldBe 14
            }
        }

        context("주문 취소 통합 시나리오") {
            it("주문을 생성하고 취소할 수 있다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 100000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // given - 주문 생성
                val createRequest = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 3)),
                    couponId = null
                )
                val orderResponse = orderService.createOrder(createRequest)

                // when - 주문 취소
                val cancelRequest = CancelOrderRequest(userId = user.id)
                val cancelResponse = orderService.cancelOrder(orderResponse.orderId, cancelRequest)

                // then - 취소 결과 검증
                cancelResponse.orderId shouldBe orderResponse.orderId
                cancelResponse.status shouldBe "CANCELLED"
                cancelResponse.refund.restoredStock.size shouldBe 1
                cancelResponse.refund.restoredStock[0].productId shouldBe product.id
                cancelResponse.refund.restoredStock[0].quantity shouldBe 3

                // then - 재고 복원 검증
                val updatedProduct = productRepository.findById(product.id)
                updatedProduct!!.stock shouldBe 10 // 원래 재고로 복원

                // then - 주문 상태 검증
                val orderDetail = orderService.getOrderDetail(orderResponse.orderId, user.id)
                orderDetail.status shouldBe "CANCELLED"
            }

            it("쿠폰을 사용한 주문을 취소하면 쿠폰이 복원된다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 100000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 100000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // given - 쿠폰 생성 및 발급
                val coupon = Coupon(
                    id = generateCouponId(),
                    name = "10% 할인 쿠폰",
                    description = "전 상품 10% 할인",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 1,
                    startDate = "2025-01-01",
                    endDate = "2025-12-31",
                    validityDays = 30,
                    createdAt = now
                )
                couponRepository.save(coupon)

                val userCoupon = UserCoupon(
                    id = couponRepository.generateUserCouponId(),
                    userId = user.id,
                    couponId = coupon.id,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now,
                    expiresAt = LocalDateTime.now().plusDays(30).format(dateFormatter),
                    usedAt = null
                )
                couponRepository.saveUserCoupon(userCoupon)

                // given - 쿠폰을 사용한 주문 생성
                val createRequest = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 1)),
                    couponId = coupon.id
                )
                val orderResponse = orderService.createOrder(createRequest)

                // when - 주문 취소
                val cancelRequest = CancelOrderRequest(userId = user.id)
                val cancelResponse = orderService.cancelOrder(orderResponse.orderId, cancelRequest)

                // then - 쿠폰 복원 검증
                cancelResponse.refund.restoredCoupon shouldNotBe null
                cancelResponse.refund.restoredCoupon!!.couponId shouldBe coupon.id
                cancelResponse.refund.restoredCoupon!!.status shouldBe "AVAILABLE"

                // then - 쿠폰 상태 직접 검증
                val restoredUserCoupon = couponRepository.findUserCoupon(user.id, coupon.id)
                restoredUserCoupon!!.status shouldBe CouponStatus.AVAILABLE
                restoredUserCoupon.usedAt shouldBe null
            }
        }

        context("주문 목록 조회 통합 시나리오") {
            it("사용자의 여러 주문을 생성하고 목록을 조회할 수 있다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 500000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 100,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // given - 여러 주문 생성
                val request1 = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 1)),
                    couponId = null
                )
                orderService.createOrder(request1)

                val request2 = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 2)),
                    couponId = null
                )
                orderService.createOrder(request2)

                val request3 = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 3)),
                    couponId = null
                )
                orderService.createOrder(request3)

                // when - 주문 목록 조회
                val orderList = orderService.getOrders(user.id, null, 0, 10)

                // then - 주문 목록 검증
                orderList.orders.size shouldBe 3
                orderList.pagination.totalElements shouldBe 3
                orderList.pagination.totalPages shouldBe 1
            }

            it("특정 상태의 주문만 필터링하여 조회할 수 있다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 500000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 100,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // given - 여러 주문 생성
                val request1 = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 1)),
                    couponId = null
                )
                val order1 = orderService.createOrder(request1)

                val request2 = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 2)),
                    couponId = null
                )
                orderService.createOrder(request2)

                // given - 하나는 취소
                orderService.cancelOrder(order1.orderId, CancelOrderRequest(user.id))

                // when - PENDING 상태 주문만 조회
                val pendingOrders = orderService.getOrders(user.id, "PENDING", 0, 10)

                // then - PENDING 주문만 조회되어야 함
                pendingOrders.orders.size shouldBe 1
                pendingOrders.orders[0].status shouldBe "PENDING"

                // when - CANCELLED 상태 주문만 조회
                val cancelledOrders = orderService.getOrders(user.id, "CANCELLED", 0, 10)

                // then - CANCELLED 주문만 조회되어야 함
                cancelledOrders.orders.size shouldBe 1
                cancelledOrders.orders[0].status shouldBe "CANCELLED"
            }
        }

        context("재고 부족 시나리오") {
            it("재고가 부족하면 주문이 실패한다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 500000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 재고가 적은 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 3, // 재고 3개만
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // when & then - 재고보다 많이 주문하면 실패
                shouldThrow<InsufficientStockException> {
                    orderService.createOrder(
                        CreateOrderRequest(
                            userId = user.id,
                            items = listOf(OrderItemRequest(product.id, 5)),
                            couponId = null
                        )
                    )
                }

                // then - 재고는 그대로 유지되어야 함
                val unchangedProduct = productRepository.findById(product.id)
                unchangedProduct!!.stock shouldBe 3
            }

            it("동일한 상품을 연속으로 주문하면 재고가 누적으로 차감된다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 500000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // when - 첫 번째 주문
                orderService.createOrder(
                    CreateOrderRequest(
                        userId = user.id,
                        items = listOf(OrderItemRequest(product.id, 3)),
                        couponId = null
                    )
                )

                // then - 재고 확인
                productRepository.findById(product.id)!!.stock shouldBe 7

                // when - 두 번째 주문
                orderService.createOrder(
                    CreateOrderRequest(
                        userId = user.id,
                        items = listOf(OrderItemRequest(product.id, 4)),
                        couponId = null
                    )
                )

                // then - 재고 확인
                productRepository.findById(product.id)!!.stock shouldBe 3

                // when & then - 재고 부족으로 실패
                shouldThrow<InsufficientStockException> {
                    orderService.createOrder(
                        CreateOrderRequest(
                            userId = user.id,
                            items = listOf(OrderItemRequest(product.id, 5)),
                            couponId = null
                        )
                    )
                }
            }
        }

        context("예외 시나리오") {
            it("존재하지 않는 주문은 조회할 수 없다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 100000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // when & then
                shouldThrow<OrderNotFoundException> {
                    orderService.getOrderDetail(999L, user.id)
                }
            }

            it("PAID 상태의 주문은 취소할 수 없다") {
                // given - 사용자 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = generateUserId(),
                    balance = 100000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(user)

                // given - 상품 생성
                val product = Product(
                    id = generateProductId(),
                    name = "노트북",
                    description = "고성능 노트북",
                    price = 50000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // given - 주문 생성
                val createRequest = CreateOrderRequest(
                    userId = user.id,
                    items = listOf(OrderItemRequest(product.id, 1)),
                    couponId = null
                )
                val orderResponse = orderService.createOrder(createRequest)

                // given - 주문 상태를 PAID로 변경 (결제 완료 시뮬레이션)
                val order = orderRepository.findById(orderResponse.orderId)!!
                order.markAsPaid()
                orderRepository.save(order)

                // when & then - PAID 상태의 주문은 취소할 수 없음
                shouldThrow<CannotCancelOrderException> {
                    orderService.cancelOrder(orderResponse.orderId, CancelOrderRequest(user.id))
                }
            }
        }
    }
})
