package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.Payment
import com.hhplus.ecommerce.domain.payment.entity.PaymentStatus
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.application.payment.dto.CancelPaymentCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.application.shipping.ShippingService
import com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
import com.hhplus.ecommerce.domain.payment.repository.PaymentJpaRepository
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class PaymentServiceUnitTest : DescribeSpec({
    lateinit var paymentRepository: PaymentJpaRepository
    lateinit var transmissionRepository: DataTransmissionJpaRepository
    lateinit var orderService: OrderService
    lateinit var userService: UserService
    lateinit var productService: ProductService
    lateinit var couponService: CouponService
    lateinit var paymentService: PaymentService
    lateinit var shippingService: ShippingService

    beforeEach {
        // 모든 의존성을 Mock으로 생성
        paymentRepository = mockk(relaxed = true)
        transmissionRepository = mockk(relaxed = true)
        orderService = mockk(relaxed = true)
        userService = mockk(relaxed = true)
        productService = mockk(relaxed = true)
        couponService = mockk(relaxed = true)
        shippingService = mockk(relaxed = true)

        paymentService = PaymentServiceImpl(
            paymentRepository,
            transmissionRepository,
            orderService,
            userService,
            productService,
            couponService,
            shippingService,
        )
    }

    describe("PaymentService 단위 테스트 - processPayment") {
        context("정상 케이스") {
            it("주문에 대한 결제를 성공적으로 처리한다") {
                // given
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val paymentId = UUID.randomUUID()
                val transmissionId = UUID.randomUUID()
                val amount = 50000L

                val user = User(balance = 100000L)

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-20251103-001",
                    totalAmount = amount,
                    discountAmount = 0L,
                    finalAmount = amount,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(7)
                val shipping = Shipping(
                    orderId = orderId,
                    carrier = "CJ대한통운",
                    trackingNumber = "trackingNumber",
                    shippingStartAt = null,
                    estimatedArrivalAt = estimatedArrivalAt,
                    deliveredAt = now,
                    status = ShippingStatus.PENDING
                )

                // id 설정
                val orderField = order.javaClass.superclass.getDeclaredField("id")
                orderField.isAccessible = true
                orderField.set(order, orderId)

                val command = ProcessPaymentCommand(userId = userId)

                // Mock 설정
                every { orderService.getOrderWithLock(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userService.findByIdWithLock(userId) } returns user
                every { userService.updateUser(any()) } returns user
                every { orderService.updateOrder(any()) } returns order
                every { shippingService.createShipping(orderId, "CJ대한통운") } returns shipping

                val savedPayment = Payment(
                    orderId = orderId,
                    userId = userId,
                    amount = amount,
                    status = PaymentStatus.SUCCESS,
                    paidAt = LocalDateTime.now()
                )
                every { paymentRepository.save(any()) } returns savedPayment
                val paymentField = savedPayment.javaClass.superclass.getDeclaredField("id")
                paymentField.isAccessible = true
                paymentField.set(savedPayment, paymentId)

                val savedTransmission = DataTransmission(
                    orderId = orderId,
                    status = TransmissionStatus.PENDING,
                    attempts = 0,
                    nextRetryAt = LocalDateTime.now().plusMinutes(1)
                )

                every { transmissionRepository.save(any()) } returns savedTransmission
                val transmissionField = savedTransmission.javaClass.superclass.getDeclaredField("id")
                transmissionField.isAccessible = true
                transmissionField.set(savedTransmission, transmissionId)

                // when
                val result = paymentService.processPayment(orderId, command)

                // then
                result.paymentId shouldBe paymentId
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
                val orderId = UUID.randomUUID()
                val command = ProcessPaymentCommand(userId = UUID.randomUUID())

                every { orderService.getOrderWithLock(orderId) } throws OrderNotFoundException(orderId)

                // when & then
                shouldThrow<OrderNotFoundException> {
                    paymentService.processPayment(orderId, command)
                }
            }

            it("다른 사용자의 주문 결제 시 OrderForbiddenException 발생") {
                // given
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-001",
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                ReflectionTestUtils.setField(order, "id", orderId)

                val command = ProcessPaymentCommand(userId = otherUserId)

                every { orderService.getOrderWithLock(orderId) } returns order

                // when & then
                shouldThrow<OrderForbiddenException> {
                    paymentService.processPayment(orderId, command)
                }
            }

            it("PENDING이 아닌 주문에 대한 결제 시 InvalidOrderStatusException 발생") {
                    // given
                    val orderId = UUID.randomUUID()
                    val userId = UUID.randomUUID()

                    val order = Order(
                        userId = userId,
                        orderNumber = "ORD-001",
                        totalAmount = 50000L,
                        discountAmount = 0L,
                        finalAmount = 50000L,
                        appliedCouponId = null,
                        status = OrderStatus.PAID  // 이미 결제됨
                    )

                    val command = ProcessPaymentCommand(userId = userId)

                every { orderService.getOrderWithLock(orderId) } returns order

                // when & then
                shouldThrow<InvalidOrderStatusException> {
                    paymentService.processPayment(orderId, command)
                }
            }

            it("이미 결제 레코드가 존재하는 경우 AlreadyPaidException 발생") {
                // given
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-001",
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                val existingPayment = Payment(
                    orderId = orderId,
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.SUCCESS,
                    paidAt = LocalDateTime.now()
                )

                val command = ProcessPaymentCommand(userId = userId)

                every { orderService.getOrderWithLock(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns existingPayment

                // when & then
                shouldThrow<AlreadyPaidException> {
                    paymentService.processPayment(orderId, command)
                }
            }

            it("잔액이 부족한 경우 InsufficientBalanceException 발생") {
                // given
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val amount = 50000L

                val user = User(balance = 10000L)  // 부족한 잔액

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-001",
                    totalAmount = amount,
                    discountAmount = 0L,
                    finalAmount = amount,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                // order id 설정
                val orderField = order.javaClass.superclass.getDeclaredField("id")
                orderField.isAccessible = true
                orderField.set(order, orderId)

                val command = ProcessPaymentCommand(userId = userId)

                every { orderService.getOrderWithLock(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userService.findByIdWithLock(userId) } returns user
                
                // handlePaymentFailure 호출을 위한 Mock 설정
                every { productService.findAllByIdWithLock(any()) } returns emptyList()
                every { productService.updateProduct(any()) } returns mockk(relaxed = true)
                every { orderService.updateOrder(any()) } returns order

                // when & then
                shouldThrow<InsufficientBalanceException> {
                    paymentService.processPayment(orderId, command)
                }
            }

            it("존재하지 않는 사용자인 경우 UserNotFoundException 발생") {
                // given
                val orderId = UUID.randomUUID()
                val userId = UUID.randomUUID()

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-001",
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING
                )

                val command = ProcessPaymentCommand(userId = userId)

                every { orderService.getOrderWithLock(orderId) } returns order
                every { paymentRepository.findByOrderId(orderId) } returns null
                every { userService.findByIdWithLock(userId) } throws UserNotFoundException(userId)

                // when & then
                shouldThrow<UserNotFoundException> {
                    paymentService.processPayment(orderId, command)
                }
            }
        }
    }

    describe("PaymentService 단위 테스트 - getPaymentDetail") {
        it("결제 ID와 사용자 ID로 결제 상세 정보를 조회한다") {
            // given
            val paymentId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val payment = Payment(
                orderId = orderId,
                userId = userId,
                amount = 50000L,
                status = PaymentStatus.SUCCESS,
                paidAt = LocalDateTime.now()
            )
            val paymentField = payment.javaClass.superclass.getDeclaredField("id")
            paymentField.isAccessible = true
            paymentField.set(payment, paymentId)

            val order = Order(
                userId = userId,
                orderNumber = "ORD-001",
                totalAmount = 50000L,
                discountAmount = 0L,
                finalAmount = 50000L,
                appliedCouponId = null,
                status = OrderStatus.PAID
            )

            val transmissionId = UUID.randomUUID()
            val transmission = DataTransmission(
                orderId = orderId,
                status = TransmissionStatus.PENDING,
                attempts = 0,
                nextRetryAt = LocalDateTime.now().plusMinutes(5)
            )
            val transmissionField = transmission.javaClass.superclass.getDeclaredField("id")
            transmissionField.isAccessible = true
            transmissionField.set(transmission, transmissionId)

            every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
            every { orderService.getOrder(orderId) } returns order
            every { transmissionRepository.findByOrderId(orderId) } returns transmission

            // when
            val result = paymentService.getPaymentDetail(paymentId, userId)

            // then
            result.paymentId shouldBe paymentId
            result.orderId shouldBe orderId
            result.userId shouldBe userId
            result.amount shouldBe 50000L
            result.paymentStatus shouldBe "SUCCESS"
            result.dataTransmission.transmissionId shouldBe transmissionId.toString()
            result.dataTransmission.status shouldBe "PENDING"
        }

        it("존재하지 않는 결제 ID인 경우 PaymentNotFoundException 발생") {
            // given
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            every { paymentRepository.findById(paymentId) } returns Optional.empty()

            // when & then
            shouldThrow<PaymentNotFoundException> {
                paymentService.getPaymentDetail(paymentId, userId)
            }
        }

        it("다른 사용자의 결제 정보 조회 시 ForbiddenException 발생") {
            // given
            val paymentId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()

            val payment = Payment(
                orderId = UUID.randomUUID(),
                userId = userId,
                amount = 50000L,
                status = PaymentStatus.SUCCESS,
                paidAt = LocalDateTime.now()
            )

            every { paymentRepository.findById(paymentId) } returns Optional.of(payment)

            // when & then
            shouldThrow<ForbiddenException> {
                paymentService.getPaymentDetail(paymentId, otherUserId)
            }
        }
    }

    describe("PaymentService 단위 테스트 - cancelPayment") {
        context("정상 케이스") {
            it("결제 취소를 성공하고, 결제자에게 잔액을 환불한다.") {
                val paymentId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val amount = 50000L

                val payment = Payment(
                    orderId = orderId,
                    userId = userId,
                    amount = amount,
                    status = PaymentStatus.SUCCESS,
                    paidAt = LocalDateTime.now()
                )
                val paymentField = payment.javaClass.superclass.getDeclaredField("id")
                paymentField.isAccessible = true
                paymentField.set(payment, paymentId)

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-20251103-001",
                    totalAmount = amount,
                    discountAmount = 0L,
                    finalAmount = amount,
                    appliedCouponId = null,
                    status = OrderStatus.PAID
                )
                val orderField = order.javaClass.superclass.getDeclaredField("id")
                orderField.isAccessible = true
                orderField.set(order, orderId)

                val user = User(balance = 50000L)  // 현재 잔액

                val command = CancelPaymentCommand(userId)

                // mock
                every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
                every { orderService.getOrder(orderId) } returns order
                every { userService.findByIdWithLock(userId) } returns user
                every { userService.updateUser(user) } returns user
                every { paymentRepository.save(any()) } answers { firstArg() }

                val result = paymentService.cancelPayment(paymentId, command)

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
                verify(exactly = 1) { paymentRepository.save(match { it.status == PaymentStatus.CANCELLED }) }
            }
        }

        context("예외 케이스") {
            it("이미 취소된 결제를 다시 취소하려는 경우 AlreadyCancelledException 발생") {
                // given
                val paymentId = UUID.randomUUID()
                val userId = UUID.randomUUID()

                val payment = Payment(
                    orderId = UUID.randomUUID(),
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.CANCELLED,  // 이미 취소됨
                    paidAt = LocalDateTime.now()
                )

                val command = CancelPaymentCommand(userId = userId)

                every { paymentRepository.findById(paymentId) } returns Optional.of(payment)

                // when & then
                shouldThrow<AlreadyCancelledException> {
                    paymentService.cancelPayment(paymentId, command)
                }

                verify(exactly = 1) { paymentRepository.findById(paymentId) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }

            it("SUCCESS 상태가 아닌 결제 취소 시도 시 InvalidPaymentStatusException 발생") {
                // given
                val paymentId = UUID.randomUUID()
                val userId = UUID.randomUUID()

                val payment = Payment(
                    orderId = UUID.randomUUID(),
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.FAILED,  // SUCCESS가 아님
                    paidAt = LocalDateTime.now()
                )

                val command = CancelPaymentCommand(userId = userId)

                every { paymentRepository.findById(paymentId) } returns Optional.of(payment)

                // when & then
                shouldThrow<InvalidPaymentStatusException> {
                    paymentService.cancelPayment(paymentId, command)
                }

                verify(exactly = 1) { paymentRepository.findById(paymentId) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }

        context("환불 검증") {
            it("환불 후 사용자의 잔액이 정확하게 증가한다.") {
                val paymentId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val paymentAmount = 80000L

                val payment = Payment(
                    orderId = orderId,
                    userId = userId,
                    amount = paymentAmount,
                    status = PaymentStatus.SUCCESS,
                    paidAt = LocalDateTime.now()
                )
                val paymentField = payment.javaClass.superclass.getDeclaredField("id")
                paymentField.isAccessible = true
                paymentField.set(payment, paymentId)

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-20251103-001",
                    totalAmount = paymentAmount,
                    discountAmount = 0L,
                    finalAmount = paymentAmount,
                    appliedCouponId = null,
                    status = OrderStatus.PAID
                )
                val orderField = order.javaClass.superclass.getDeclaredField("id")
                orderField.isAccessible = true
                orderField.set(order, orderId)

                val user = User(balance = 25000L)  // 현재 잔액

                val command = CancelPaymentCommand(userId)

                // Mock 설정
                every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
                every { orderService.getOrder(orderId) } returns order
                every { userService.findByIdWithLock(userId) } returns user
                every { userService.updateUser(any()) } returns user
                every { paymentRepository.save(any()) } answers { firstArg() }

                // when
                val result = paymentService.cancelPayment(paymentId, command)

                // then
                result.balance.previousBalance shouldBe 25000L
                result.balance.refundedAmount shouldBe 80000L
                result.balance.currentBalance shouldBe 105000L

                verify(exactly = 1) { userService.updateUser(any()) }
            }

            it("결제 상태가 SUCCESS에서 CANCELLED로 변경된다") {
                // given
                val paymentId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val orderId = UUID.randomUUID()

                val payment = Payment(
                    orderId = orderId,
                    userId = userId,
                    amount = 50000L,
                    status = PaymentStatus.SUCCESS,
                    paidAt = LocalDateTime.now()
                )
                val paymentField = payment.javaClass.superclass.getDeclaredField("id")
                paymentField.isAccessible = true
                paymentField.set(payment, paymentId)

                val order = Order(
                    userId = userId,
                    orderNumber = "ORD-001",
                    totalAmount = 50000L,
                    discountAmount = 0L,
                    finalAmount = 50000L,
                    appliedCouponId = null,
                    status = OrderStatus.PAID
                )
                val orderField = order.javaClass.superclass.getDeclaredField("id")
                orderField.isAccessible = true
                orderField.set(order, orderId)

                val user = User(balance = 50000L)

                val command = CancelPaymentCommand(userId = userId)

                // Mock 설정
                every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
                every { orderService.getOrder(orderId) } returns order
                every { userService.getUser(userId) } returns user
                every { userService.updateUser(any()) } returns user

                // save 호출 시 상태 변경 확인을 위한 slot
                val savedPaymentSlot = slot<Payment>()
                every { paymentRepository.save(capture(savedPaymentSlot)) } answers { firstArg() }

                // when
                val result = paymentService.cancelPayment(paymentId, command)

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
