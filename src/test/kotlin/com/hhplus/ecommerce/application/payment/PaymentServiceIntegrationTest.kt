package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.cart.CartServiceImpl
import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.coupon.CouponServiceImpl
import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.order.OrderServiceImpl
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.product.ProductServiceImpl
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.UserServiceImpl
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.LockManager
import com.hhplus.ecommerce.domain.coupon.repository.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepositoryImpl
import com.hhplus.ecommerce.domain.order.OrderRepository
import com.hhplus.ecommerce.infrastructure.order.OrderRepositoryImpl
import com.hhplus.ecommerce.domain.payment.PaymentRepository
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.infrastructure.payment.PaymentRepositoryImpl
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepositoryImpl
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.infrastructure.user.UserRepositoryImpl
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PaymentServiceIntegrationTest : DescribeSpec({
    lateinit var paymentRepository: PaymentRepository
    lateinit var orderRepository: OrderRepository
    lateinit var userRepository: UserRepository
    lateinit var productRepository: ProductRepository
    lateinit var couponRepository: CouponRepository
    lateinit var cartRepository: CartRepository
    lateinit var paymentService: PaymentService
    lateinit var orderService: OrderService
    lateinit var productService: ProductService
    lateinit var couponService: CouponService
    lateinit var userService: UserService
    lateinit var cartService: CartService

    beforeEach {
        // 실제 구현체 사용
        paymentRepository = PaymentRepositoryImpl()
        orderRepository = OrderRepositoryImpl()
        userRepository = UserRepositoryImpl()
        productRepository = ProductRepositoryImpl()
        couponRepository = CouponRepositoryImpl()
        cartRepository = CartRepositoryImpl()

        val lockManager = LockManager()

        productService = ProductServiceImpl(productRepository)
        couponService = CouponServiceImpl(couponRepository, lockManager)
        userService = UserServiceImpl(userRepository)
        cartService = CartServiceImpl(cartRepository, productService, userService)

        orderService = OrderServiceImpl(
            orderRepository,
            productService,
            couponService,
            userService,
            cartService,
            lockManager
        )

        paymentService = PaymentServiceImpl(
            paymentRepository,
            orderService,
            userService,
            productService,
            couponService,
            lockManager
        )
    }

    describe("PaymentService 통합 테스트 - Service와 Repository 통합") {

        context("결제 처리 통합 시나리오") {
            it("주문 생성 후 결제를 처리할 수 있다") {
                // given - 사용자 생성
                val createUserCommand = CreateUserCommand(balance = 2000000L)
                val user = userService.createUser(createUserCommand)

                // given - 상품 조회 (실제 데이터 사용)
                val products = productRepository.findAll()
                val productId = products.first().id

                // given - 주문 생성
                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                // when - 결제 처리
                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                val result = paymentService.processPayment(order.orderId, paymentCommand)

                // then
                result.paymentId shouldNotBe null
                result.orderId shouldBe order.orderId
                result.userId shouldBe user.id
                result.amount shouldBe order.pricing.finalAmount
                result.paymentStatus shouldBe "SUCCESS"
                result.orderStatus shouldBe "PAID"
                result.balance.previousBalance shouldBe 2000000L
                result.balance.paidAmount shouldBe order.pricing.finalAmount
                (result.balance.remainingBalance < 2000000L) shouldBe true
                result.dataTransmission.status shouldBe "PENDING"
                result.paidAt shouldNotBe null
            }

            it("결제 후 사용자 잔액이 감소한다") {
                // given
                val createUserCommand = CreateUserCommand(balance = 2000000L)
                val user = userService.createUser(createUserCommand)

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                // 결제 전 잔액 확인
                val userBefore = userService.getUser(user.id)
                val balanceBefore = userBefore.balance

                // when
                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                val result = paymentService.processPayment(order.orderId, paymentCommand)

                // then - 잔액 감소 확인
                val userAfter = userService.getUser(user.id)
                val balanceAfter = userAfter.balance

                balanceAfter shouldBe (balanceBefore - order.pricing.finalAmount)
                result.balance.previousBalance shouldBe balanceBefore
                result.balance.remainingBalance shouldBe balanceAfter
            }

            it("결제 후 주문 상태가 PAID로 변경된다") {
                // given
                val createUserCommand = CreateUserCommand(balance = 2000000L)
                val user = userService.createUser(createUserCommand)

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                // 결제 전 주문 상태 확인
                val orderBefore = orderService.getOrderDetail(order.orderId, user.id)
                orderBefore.status shouldBe "PENDING"

                // when
                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                paymentService.processPayment(order.orderId, paymentCommand)

                // then - 주문 상태 확인
                val orderAfter = orderService.getOrderDetail(order.orderId, user.id)
                orderAfter.status shouldBe "PAID"
            }

            it("결제 처리 후 데이터 전송 레코드가 생성된다") {
                // given
                val createUserCommand = CreateUserCommand(balance = 2000000L)
                val user = userService.createUser(createUserCommand)

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                // when
                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                val result = paymentService.processPayment(order.orderId, paymentCommand)

                // then - 데이터 전송 정보 확인
                result.dataTransmission shouldNotBe null
                result.dataTransmission.transmissionId shouldNotBe null
                result.dataTransmission.status shouldBe "PENDING"
                result.dataTransmission.scheduledAt shouldNotBe null

                // 전송 상세 정보 조회 가능한지 확인
                val transmissionDetail = paymentService.getTransmissionDetail(result.dataTransmission.transmissionId)
                transmissionDetail.transmissionId shouldBe result.dataTransmission.transmissionId
                transmissionDetail.status shouldBe "PENDING"
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 주문에 대한 결제 시 예외가 발생한다") {
                // given
                val command = ProcessPaymentCommand(userId = 1L)

                // when & then
                shouldThrow<OrderNotFoundException> {
                    paymentService.processPayment(999999L, command)
                }
            }

            it("다른 사용자의 주문을 결제할 수 없다") {
                // given - 첫 번째 사용자가 주문 생성
                val user1 = userService.createUser(CreateUserCommand(balance = 2000000L))
                val user2 = userService.createUser(CreateUserCommand(balance = 2000000L))

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user1.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                // when & then - 두 번째 사용자가 결제 시도
                val paymentCommand = ProcessPaymentCommand(userId = user2.id)
                shouldThrow<ForbiddenException> {
                    paymentService.processPayment(order.orderId, paymentCommand)
                }
            }

            it("잔액이 부족하면 결제가 실패한다") {
                // given - 잔액이 부족한 사용자
                val user = userService.createUser(CreateUserCommand(balance = 1000L))

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                // when & then
                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                shouldThrow<InsufficientBalanceException> {
                    paymentService.processPayment(order.orderId, paymentCommand)
                }
            }

            it("이미 결제된 주문은 다시 결제할 수 없다") {
                // given - 결제 완료
                val user = userService.createUser(CreateUserCommand(balance = 2000000L))

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                paymentService.processPayment(order.orderId, paymentCommand)

                // when & then - 다시 결제 시도
                shouldThrow<InvalidOrderStatusException> {
                    paymentService.processPayment(order.orderId, paymentCommand)
                }
            }
        }

        context("데이터 전송 재시도") {
            it("FAILED 상태의 전송을 재시도할 수 있다") {
                // given - 결제 완료 및 전송 실패로 설정
                val user = userService.createUser(CreateUserCommand(balance = 2000000L))

                val products = productRepository.findAll()
                val productId = products.first().id

                val createOrderCommand = CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        OrderItemCommand(productId = productId, quantity = 1)
                    )
                )
                val order = orderService.createOrder(createOrderCommand)

                val paymentCommand = ProcessPaymentCommand(userId = user.id)
                val payment = paymentService.processPayment(order.orderId, paymentCommand)

                val transmissionId = payment.dataTransmission.transmissionId

                // 전송을 FAILED 상태로 만들기 (실제로는 외부 시스템 호출 실패로 발생)
                val transmission = paymentRepository.findTransmissionById(transmissionId)!!
                val failedTransmission = transmission.copy(
                    status = TransmissionStatus.FAILED,
                    errorMessage = "Test error"
                )
                paymentRepository.saveTransmission(failedTransmission)

                // when - 재시도
                val result = paymentService.retryTransmission(transmissionId)

                // then
                result.transmissionId shouldBe transmissionId
                result.status shouldBe "SUCCESS"
                result.retriedAt shouldNotBe null
                result.attempts shouldNotBe null
            }

            it("존재하지 않는 전송 ID 재시도 시 예외가 발생한다") {
                // when & then
                shouldThrow<TransmissionNotFoundException> {
                    paymentService.retryTransmission(999999L)
                }
            }
        }
    }
})
