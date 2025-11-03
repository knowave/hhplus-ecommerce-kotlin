package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.order.OrderRepository
import com.hhplus.ecommerce.infrastructure.payment.PaymentRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepository
import com.hhplus.ecommerce.infrastructure.user.UserRepository
import com.hhplus.ecommerce.model.order.Order
import com.hhplus.ecommerce.model.order.OrderItem
import com.hhplus.ecommerce.model.order.OrderStatus
import com.hhplus.ecommerce.model.payment.DataTransmission
import com.hhplus.ecommerce.model.payment.Payment
import com.hhplus.ecommerce.model.payment.PaymentStatus
import com.hhplus.ecommerce.model.payment.TransmissionStatus
import com.hhplus.ecommerce.model.user.User
import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.LocalDateTime

class PaymentServiceUnitTest : DescribeSpec({
    lateinit var paymentRepository: PaymentRepository
    lateinit var orderRepository: OrderRepository
    lateinit var userRepository: UserRepository
    lateinit var productRepository: ProductRepository
    lateinit var couponRepository: CouponRepository
    lateinit var paymentService: PaymentService

    beforeEach {
        // 모든 의존성을 Mock으로 생성
        paymentRepository = mockk()
        orderRepository = mockk()
        userRepository = mockk()
        productRepository = mockk()
        couponRepository = mockk()
        paymentService = PaymentServiceImpl(
            paymentRepository,
            orderRepository,
            userRepository,
            productRepository,
            couponRepository
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
                every { orderRepository.findById(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userRepository.findById(userId) } returns user
                every { userRepository.save(any()) } returns user.copy(balance = 50000L)
                every { orderRepository.save(any()) } answers {
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

                // Repository 호출 검증
                verify(exactly = 1) { orderRepository.findById(orderId) }
                verify(exactly = 1) { paymentRepository.findByOrderId(orderId) }
                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { userRepository.save(any()) }
                verify(exactly = 1) { orderRepository.save(any()) }
                verify(exactly = 1) { paymentRepository.save(any()) }
                verify(exactly = 1) { paymentRepository.saveTransmission(any()) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 주문에 대한 결제 시 OrderNotFoundException 발생") {
                // given
                val orderId = 999L
                val request = ProcessPaymentRequest(userId = 1L)

                every { orderRepository.findById(orderId) } returns null

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

                every { orderRepository.findById(orderId) } returns order

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

                every { orderRepository.findById(orderId) } returns order

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

                every { orderRepository.findById(orderId) } returns order
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

                every { orderRepository.findById(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userRepository.findById(userId) } returns user
                // handlePaymentFailure 호출을 위한 Mock 설정
                every { orderRepository.save(any()) } answers { firstArg() }
                every { productRepository.findById(any()) } returns mockk(relaxed = true)
                every { productRepository.save(any()) } returns mockk(relaxed = true)
                every { couponRepository.findById(any()) } returns null

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

                every { orderRepository.findById(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userRepository.findById(userId) } returns null

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
            every { orderRepository.findById(1L) } returns order
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

    describe("PaymentService 단위 테스트 - retryTransmission") {
        it("FAILED 상태의 전송을 재시도할 수 있다") {
            // given
            val transmissionId = 1L
            val now = LocalDateTime.now()

            val transmission = DataTransmission(
                transmissionId = transmissionId,
                orderId = 1L,
                status = TransmissionStatus.FAILED,
                attempts = 1,
                maxAttempts = 3,
                createdAt = now.minusHours(1),
                sentAt = now.minusMinutes(5),
                nextRetryAt = null,
                errorMessage = "Network error"
            )

            every { paymentRepository.findTransmissionById(transmissionId) } returns transmission
            every { paymentRepository.saveTransmission(any()) } answers { firstArg() }

            // when
            val result = paymentService.retryTransmission(transmissionId)

            // then
            result.transmissionId shouldBe transmissionId
            result.status shouldBe "SUCCESS"  // 재시도는 항상 SUCCESS로 처리됨
            result.retriedAt shouldNotBe null
            result.attempts shouldBe 2

            verify(exactly = 1) { paymentRepository.saveTransmission(any()) }
        }

        it("이미 성공한 전송은 재시도할 수 없다") {
            // given
            val transmissionId = 1L
            val now = LocalDateTime.now()

            val transmission = DataTransmission(
                transmissionId = transmissionId,
                orderId = 1L,
                status = TransmissionStatus.SUCCESS,
                attempts = 0,
                maxAttempts = 3,
                createdAt = now,
                sentAt = now.plusMinutes(5),
                nextRetryAt = null,
                errorMessage = null
            )

            every { paymentRepository.findTransmissionById(transmissionId) } returns transmission

            // when & then
            shouldThrow<AlreadySuccessException> {
                paymentService.retryTransmission(transmissionId)
            }
        }

        it("존재하지 않는 전송 ID인 경우 TransmissionNotFoundException 발생") {
            // given
            val transmissionId = 999L

            every { paymentRepository.findTransmissionById(transmissionId) } returns null

            // when & then
            shouldThrow<TransmissionNotFoundException> {
                paymentService.retryTransmission(transmissionId)
            }
        }
    }
})
