package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.LockManager
import com.hhplus.ecommerce.domain.payment.PaymentRepository
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.Payment
import com.hhplus.ecommerce.domain.payment.entity.PaymentStatus
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentRequest
import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentRequest
import com.hhplus.ecommerce.presentation.user.dto.UserInfoResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.LocalDateTime

class PaymentServiceUnitTest : DescribeSpec({
    lateinit var paymentRepository: PaymentRepository
    lateinit var orderService: OrderService
    lateinit var userService: UserService
    lateinit var productService: ProductService
    lateinit var couponService: CouponService
    lateinit var paymentService: PaymentService
    lateinit var lockManager: LockManager

    beforeEach {
        // 모든 의존성을 Mock으로 생성
        paymentRepository = mockk()
        orderService = mockk()
        userService = mockk()
        productService = mockk()
        couponService = mockk()
        lockManager = LockManager()
        paymentService = PaymentServiceImpl(
            paymentRepository,
            orderService,
            userService,
            productService,
            couponService,
            lockManager
        )
    }

    describe("PaymentService 단위 테스트 - processPayment") {
        context("정상 케이스") {
            it("주문에 대한 결제를 성공적으로 처리한다") {
                // given
                val orderId = 1L
                val userId = 100L
                val amount = 50000L
                val now = LocalDateTime.now()

                val user = User(
                    id = userId,
                    balance = 100000L,
                    createdAt = "2025-11-03T00:00:00",
                    updatedAt = "2025-11-03T00:00:00"
                )

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-20251103-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = amount,
                    discountAmount = 0L,
                    finalAmount = amount,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val request = ProcessPaymentRequest(userId = userId)

                // Mock 설정
                every { orderService.getOrder(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userService.getUser(userId) } returns user
                every { userService.updateUser(any()) } returns user
                every { orderService.updateOrder(any()) } answers {
                    val savedOrder = firstArg<Order>()
                    savedOrder.copy()
                }
                every { paymentRepository.generateId() } returns 1L
                every { paymentRepository.save(any()) } answers { firstArg() }
                every { paymentRepository.generateTransmissionId() } returns 1L
                every { paymentRepository.saveTransmission(any()) } answers { firstArg() }

                // when
                val result = paymentService.processPayment(orderId, request)

                // then
                result.paymentId shouldBe 1L
                result.orderId shouldBe orderId
                result.userId shouldBe userId
                result.amount shouldBe amount
                result.paymentStatus shouldBe "SUCCESS"
                result.orderStatus shouldBe "PAID"
                result.balance.previousBalance shouldBe 100000L
                result.balance.paidAmount shouldBe amount
                result.balance.remainingBalance shouldBe 50000L
                result.dataTransmission.status shouldBe "PENDING"
                result.paidAt shouldNotBe null
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 주문에 대한 결제 시 OrderNotFoundException 발생") {
                // given
                val orderId = 999L
                val request = ProcessPaymentRequest(userId = 1L)

                every { orderService.getOrder(orderId) } throws OrderNotFoundException(orderId)

                // when & then
                shouldThrow<OrderNotFoundException> {
                    paymentService.processPayment(orderId, request)
                }
            }

            it("다른 사용자의 주문 결제 시 ForbiddenException 발생") {
                // given
                val orderId = 1L
                val userId = 100L
                val otherUserId = 200L
                val now = LocalDateTime.now()

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val request = ProcessPaymentRequest(userId = otherUserId)

                every { orderService.getOrder(orderId) } returns order

                // when & then
                shouldThrow<ForbiddenException> {
                    paymentService.processPayment(orderId, request)
                }
            }

            it("PENDING이 아닌 주문에 대한 결제 시 InvalidOrderStatusException 발생") {
                // given
                val orderId = 1L
                val userId = 100L
                val now = LocalDateTime.now()

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PAID,  // 이미 결제됨
                    createdAt = now,
                    updatedAt = now
                )

                val request = ProcessPaymentRequest(userId = userId)

                every { orderService.getOrder(orderId) } returns order

                // when & then
                shouldThrow<InvalidOrderStatusException> {
                    paymentService.processPayment(orderId, request)
                }
            }

            it("이미 결제 레코드가 존재하는 경우 AlreadyPaidException 발생") {
                // given
                val orderId = 1L
                val userId = 100L
                val now = LocalDateTime.now()

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val existingPayment = Payment(
                    paymentId = 1L,
                    orderId = orderId,
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.SUCCESS,
                    paidAt = now
                )

                val request = ProcessPaymentRequest(userId = userId)

                every { orderService.getOrder(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns existingPayment

                // when & then
                shouldThrow<AlreadyPaidException> {
                    paymentService.processPayment(orderId, request)
                }
            }

            it("잔액이 부족한 경우 InsufficientBalanceException 발생") {
                // given
                val orderId = 1L
                val userId = 100L
                val amount = 50000L
                val now = LocalDateTime.now()

                val user = User(
                    id = userId,
                    balance = 10000L,  // 부족한 잔액
                    createdAt = "2025-11-03T00:00:00",
                    updatedAt = "2025-11-03T00:00:00"
                )

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = amount,
                    discountAmount = 0L,
                    finalAmount = amount,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val request = ProcessPaymentRequest(userId = userId)

                every { orderService.getOrder(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userService.getUser(userId) } returns user
                // handlePaymentFailure 호출을 위한 Mock 설정
                every { orderService.updateOrder(any()) } answers { firstArg() }
                every { productService.findProductById(any()) } returns mockk(relaxed = true)
                every { productService.updateProduct(any()) } returns mockk(relaxed = true)

                // when & then
                shouldThrow<InsufficientBalanceException> {
                    paymentService.processPayment(orderId, request)
                }
            }

            it("존재하지 않는 사용자인 경우 UserNotFoundException 발생") {
                // given
                val orderId = 1L
                val userId = 999L
                val now = LocalDateTime.now()

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                val request = ProcessPaymentRequest(userId = userId)

                every { userService.getUser(userId) } throws UserNotFoundException(userId)
                every { orderService.getOrder(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    paymentService.processPayment(orderId, request)
                }
            }
        }
    }

    describe("PaymentService 단위 테스트 - getPaymentDetail") {
        it("결제 ID와 사용자 ID로 결제 상세 정보를 조회한다") {
            // given
            val paymentId = 1L
            val userId = 100L
            val now = LocalDateTime.now()

            val payment = Payment(
                paymentId = paymentId,
                orderId = 1L,
                userId = userId,
                amount = 50000L,
                status = PaymentStatus.SUCCESS,
                paidAt = now
            )

            val order = Order(
                id = 1L,
                userId = userId,
                orderNumber = "ORD-001",
                items = listOf(
                    OrderItem(
                        id = 1L,
                        productId = 1L,
                        orderId = 1L,
                        productName = "Test Product",
                        quantity = 1,
                        unitPrice = 50000L,
                        subtotal = 50000L
                    )
                ),
                totalAmount = 50000L,
                discountAmount = 0L,
                finalAmount = 50000L,
                appliedCouponId = null,
                status = OrderStatus.PAID,
                createdAt = now,
                updatedAt = now
            )

            val transmission = DataTransmission(
                transmissionId = 1L,
                orderId = 1L,
                status = TransmissionStatus.PENDING,
                attempts = 0,
                maxAttempts = 3,
                createdAt = now,
                sentAt = null,
                nextRetryAt = now.plusMinutes(5),
                errorMessage = null
            )

            every { paymentRepository.findById(paymentId) } returns payment
            every { orderService.getOrder(1L) } returns order
            every { paymentRepository.findTransmissionByOrderId(1L) } returns transmission

            // when
            val result = paymentService.getPaymentDetail(paymentId, userId)

            // then
            result.paymentId shouldBe paymentId
            result.orderId shouldBe 1L
            result.userId shouldBe userId
            result.amount shouldBe 50000L
            result.paymentStatus shouldBe "SUCCESS"
            result.dataTransmission.transmissionId shouldBe 1L
            result.dataTransmission.status shouldBe "PENDING"
        }

        it("존재하지 않는 결제 ID인 경우 PaymentNotFoundException 발생") {
            // given
            val paymentId = 999L
            val userId = 100L

            every { paymentRepository.findById(paymentId) } returns null

            // when & then
            shouldThrow<PaymentNotFoundException> {
                paymentService.getPaymentDetail(paymentId, userId)
            }
        }

        it("다른 사용자의 결제 정보 조회 시 ForbiddenException 발생") {
            // given
            val paymentId = 1L
            val userId = 100L
            val otherUserId = 200L
            val now = LocalDateTime.now()

            val payment = Payment(
                paymentId = paymentId,
                orderId = 1L,
                userId = userId,
                amount = 50000L,
                status = PaymentStatus.SUCCESS,
                paidAt = now
            )

            every { paymentRepository.findById(paymentId) } returns payment

            // when & then
            shouldThrow<ForbiddenException> {
                paymentService.getPaymentDetail(paymentId, otherUserId)
            }
        }
    }

    describe("PaymentService 단위 테스트 - cancelPayment") {
        context("정상 케이스") {
            it("결제 취소를 성공하고, 결제자에게 잔액을 환불한다.") {
                val paymentId = 1L
                val userId =  100L
                val orderId = 1L
                val amount = 50000L
                val now = LocalDateTime.now()

                val payment = Payment(
                    paymentId,
                    orderId,
                    userId,
                    amount,
                    status = PaymentStatus.SUCCESS,
                    paidAt = now
                )

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-20251103-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = amount,
                    discountAmount = 0L,
                    finalAmount = amount,
                    appliedCouponId = null,
                    status = OrderStatus.PAID,
                    createdAt = now,
                    updatedAt = now
                )

                val user = User(
                    id = userId,
                    balance = 50000L,  // 현재 잔액
                    createdAt = "2025-11-03T00:00:00",
                    updatedAt = "2025-11-03T00:00:00"
                )

                val refundedUser = user.copy(balance = 100000L)
                val request = CancelPaymentRequest(userId)

                // mock
                every { paymentRepository.findById(paymentId) } returns payment
                every { orderService.getOrder(orderId) } returns order
                every { userService.getUser(userId) } returns user
                every { userService.updateUser(any()) } returns refundedUser
                every { paymentRepository.save(any()) } answers { firstArg() }

                val result = paymentService.cancelPayment(paymentId, request)

                result.paymentId shouldBe paymentId
                result.orderId shouldBe orderId
                result.userId shouldBe userId
                result.refundedAmount shouldBe amount
                result.paymentStatus shouldBe "CANCELLED"
                result.orderStatus shouldBe "PAID"
                result.balance.previousBalance shouldBe 50000L
                result.balance.refundedAmount shouldBe amount
                result.balance.currentBalance shouldBe 100000L
                result.cancelledAt shouldNotBe null

                verify(exactly = 1) { paymentRepository.findById(paymentId) }
                verify(exactly = 1) { orderService.getOrder(orderId) }
                verify(exactly = 1) { userService.getUser(userId) }
                verify(exactly = 1) { userService.updateUser(any()) }
                verify(exactly = 1) { paymentRepository.save(match { it.status == PaymentStatus.CANCELLED }) }
            }
        }

        context("예외 케이스") {
            it("이미 취소된 결제를 다시 취소하려는 경우 AlreadyCancelledException 발생") {
                // given
                val paymentId = 1L
                val userId = 100L
                val now = LocalDateTime.now()

                val payment = Payment(
                    paymentId = paymentId,
                    orderId = 1L,
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.CANCELLED,  // 이미 취소됨
                    paidAt = now
                )

                val request = CancelPaymentRequest(userId = userId)

                every { paymentRepository.findById(paymentId) } returns payment

                // when & then
                shouldThrow<AlreadyCancelledException> {
                    paymentService.cancelPayment(paymentId, request)
                }

                verify(exactly = 1) { paymentRepository.findById(paymentId) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }

            it("SUCCESS 상태가 아닌 결제 취소 시도 시 InvalidPaymentStatusException 발생") {
                // given
                val paymentId = 1L
                val userId = 100L
                val now = LocalDateTime.now()

                val payment = Payment(
                    paymentId = paymentId,
                    orderId = 1L,
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.FAILED,  // SUCCESS가 아님
                    paidAt = now
                )

                val request = CancelPaymentRequest(userId = userId)

                every { paymentRepository.findById(paymentId) } returns payment

                // when & then
                shouldThrow<InvalidPaymentStatusException> {
                    paymentService.cancelPayment(paymentId, request)
                }

                verify(exactly = 1) { paymentRepository.findById(paymentId) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }

        context("환불 검증") {
            it("환불 후 사용자의 잔액이 정확하게 증가한다.") {
                val paymentId = 1L
                val userId = 10L
                val orderId = 5L
                val paymentAmount = 80000L
                val now = LocalDateTime.now()

                val payment = Payment(
                    paymentId = paymentId,
                    orderId = orderId,
                    userId = userId,
                    amount = paymentAmount,
                    status = PaymentStatus.SUCCESS,
                    paidAt = now
                )

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-20251103-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 75000L,
                            subtotal = 75000L
                        )
                    ),
                    totalAmount = paymentAmount,
                    discountAmount = 0L,
                    finalAmount = paymentAmount,
                    appliedCouponId = null,
                    status = OrderStatus.PAID,
                    createdAt = now,
                    updatedAt = now
                )

                val user = User(
                    id = userId,
                    balance = 25000L,  // 현재 잔액
                    createdAt = "2025-11-03T00:00:00",
                    updatedAt = "2025-11-03T00:00:00"
                )

                val refundedUser = user.copy(balance = 100000L)
                val request = CancelPaymentRequest(userId)

                // Mock 설정
                every { paymentRepository.findById(paymentId) } returns payment
                every { orderService.getOrder(orderId) } returns order
                every { userService.getUser(userId) } returns user
                every { userService.updateUser(any()) } returns refundedUser
                every { paymentRepository.save(any()) } answers { firstArg() }

                // when
                val result = paymentService.cancelPayment(paymentId, request)

                // then
                result.balance.previousBalance shouldBe 25000L
                result.balance.refundedAmount shouldBe 80000L
                result.balance.currentBalance shouldBe 105000L

                verify(exactly = 1) { userService.updateUser(any()) }
            }

            it("결제 상태가 SUCCESS에서 CANCELLED로 변경된다") {
                // given
                val paymentId = 1L
                val userId = 100L
                val orderId = 1L
                val now = LocalDateTime.now()

                val payment = Payment(
                    paymentId = paymentId,
                    orderId = orderId,
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.SUCCESS,
                    paidAt = now
                )

                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = "ORD-001",
                    items = listOf(
                        OrderItem(
                            id = 1L,
                            productId = 1L,
                            orderId = orderId,
                            productName = "Test Product",
                            quantity = 1,
                            unitPrice = 50000L,
                            subtotal = 50000L
                        )
                    ),
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PAID,
                    createdAt = now,
                    updatedAt = now
                )

                val user = User(
                    id = userId,
                    balance = 50000L,
                    createdAt = "2025-11-03T00:00:00",
                    updatedAt = "2025-11-03T00:00:00"
                )

                val request = CancelPaymentRequest(userId = userId)

                // Mock 설정
                every { paymentRepository.findById(paymentId) } returns payment
                every { orderService.getOrder(orderId) } returns order
                every { userService.getUser(userId) } returns user
                every { userService.updateUser(any()) } returns user

                // save 호출 시 상태 변경 확인을 위한 slot
                val savedPaymentSlot = slot<Payment>()
                every { paymentRepository.save(capture(savedPaymentSlot)) } answers { firstArg() }

                // when
                val result = paymentService.cancelPayment(paymentId, request)

                // then
                result.paymentStatus shouldBe "CANCELLED"
                savedPaymentSlot.captured.status shouldBe PaymentStatus.CANCELLED

                verify(exactly = 1) {
                    paymentRepository.save(match {
                        it.status == PaymentStatus.CANCELLED
                    })
                }
            }
        }
    }
})
