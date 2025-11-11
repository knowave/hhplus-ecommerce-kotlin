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
import com.hhplus.ecommerce.domain.order.repository.OrderRepository
import com.hhplus.ecommerce.infrastructure.order.OrderRepositoryImpl
import com.hhplus.ecommerce.domain.payment.repository.PaymentRepository
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.infrastructure.payment.PaymentRepositoryImpl
import com.hhplus.ecommerce.domain.product.repository.ProductRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepositoryImpl
import com.hhplus.ecommerce.domain.user.repository.UserRepository
import com.hhplus.ecommerce.infrastructure.user.UserRepositoryImpl
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
import com.hhplus.ecommerce.domain.payment.repository.PaymentJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
class PaymentServiceIntegrationTest(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val transmissionJpaRepository: DataTransmissionJpaRepository,
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val paymentService: PaymentService
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testUserId: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID

    init {
        beforeEach {
            // 테스트용 사용자 생성
            val createUserCommand = CreateUserCommand(balance = 2000000L)
            val savedUser = userService.createUser(createUserCommand)
            testUserId = savedUser.id!!

            // 테스트용 상품 생성
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
        }

        afterEach {
            // 테스트 데이터 정리
            transmissionJpaRepository.deleteAll()
            paymentJpaRepository.deleteAll()
        }

    describe("PaymentService 통합 테스트 - 결제 전체 플로우") {

        context("결제 처리 통합 시나리오") {
            it("주문 생성 후 결제를 처리할 수 있다") {
                // given - 주문 생성
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                // when - 결제 처리
                val paymentCommand = ProcessPaymentCommand(userId = testUserId)
                val result = paymentService.processPayment(order.orderId, paymentCommand)

                // then - 결제 생성 검증
                result.paymentId shouldNotBe null
                result.orderId shouldBe order.orderId
                result.userId shouldBe testUserId
                result.amount shouldBe order.pricing.finalAmount
                result.paymentStatus shouldBe "SUCCESS"
                result.orderStatus shouldBe "PAID"
                result.balance.previousBalance shouldBe 2000000L
                result.balance.paidAmount shouldBe order.pricing.finalAmount
                result.balance.remainingBalance shouldBe (2000000L - order.pricing.finalAmount)
                result.dataTransmission.status shouldBe "PENDING"
                result.paidAt shouldNotBe null
            }

            it("결제 후 사용자 잔액이 감소한다") {
                // given - 주문 생성
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                // 결제 전 잔액 확인
                val userBefore = userService.getUser(testUserId)
                val balanceBefore = userBefore.balance

                // when - 결제 처리
                val paymentCommand = ProcessPaymentCommand(userId = testUserId)
                val result = paymentService.processPayment(order.orderId, paymentCommand)

                // then - 잔액 감소 확인
                val userAfter = userService.getUser(testUserId)
                val balanceAfter = userAfter.balance

                balanceAfter shouldBe (balanceBefore - order.pricing.finalAmount)
                result.balance.previousBalance shouldBe balanceBefore
                result.balance.remainingBalance shouldBe balanceAfter
                result.balance.paidAmount shouldBe order.pricing.finalAmount
            }

            it("결제 후 주문 상태가 PAID로 변경된다") {
                // given - 주문 생성
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                // 결제 전 주문 상태 확인
                val orderBefore = orderService.getOrderDetail(order.orderId, testUserId)
                orderBefore.status shouldBe "PENDING"

                // when - 결제 처리
                val paymentCommand = ProcessPaymentCommand(userId = testUserId)
                paymentService.processPayment(order.orderId, paymentCommand)

                // then - 주문 상태 확인
                val orderAfter = orderService.getOrderDetail(order.orderId, testUserId)
                orderAfter.status shouldBe "PAID"
            }

            it("결제 처리 후 데이터 전송 레코드가 생성된다") {
                // given - 주문 생성
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                // when - 결제 처리
                val paymentCommand = ProcessPaymentCommand(userId = testUserId)
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

        context("결제 실패 케이스") {
            it("존재하지 않는 주문에 대한 결제 시 예외가 발생한다") {
                // given
                val nonExistentOrderId = UUID.randomUUID()
                val command = ProcessPaymentCommand(userId = testUserId)

                // when & then
                shouldThrow<OrderNotFoundException> {
                    paymentService.processPayment(nonExistentOrderId, command)
                }
            }

            it("다른 사용자의 주문을 결제할 수 없다") {
                // given - 다른 사용자 생성
                val user2 = userService.createUser(CreateUserCommand(balance = 2000000L))

                // given - 첫 번째 사용자가 주문 생성
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                // when & then - 두 번째 사용자가 결제 시도
                val paymentCommand = ProcessPaymentCommand(userId = user2.id!!)
                shouldThrow<ForbiddenException> {
                    paymentService.processPayment(order.orderId, paymentCommand)
                }
            }

            it("잔액이 부족하면 결제가 실패한다") {
                // given - 잔액이 부족한 사용자 생성
                val poorUser = userService.createUser(CreateUserCommand(balance = 1000L))

                // given - 주문 생성 (상품 가격 100000원 > 잔액 1000원)
                val createOrderCommand = CreateOrderCommand(
                    userId = poorUser.id!!,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                // when & then - 결제 시도
                val paymentCommand = ProcessPaymentCommand(userId = poorUser.id!!)
                shouldThrow<InsufficientBalanceException> {
                    paymentService.processPayment(order.orderId, paymentCommand)
                }
            }

            it("이미 결제된 주문은 다시 결제할 수 없다") {
                // given - 주문 생성 및 결제 완료
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                val paymentCommand = ProcessPaymentCommand(userId = testUserId)
                paymentService.processPayment(order.orderId, paymentCommand)

                // when & then - 다시 결제 시도
                shouldThrow<InvalidOrderStatusException> {
                    paymentService.processPayment(order.orderId, paymentCommand)
                }
            }
        }

        context("데이터 전송 관리") {
            it("전송 상세 정보를 조회할 수 있다") {
                // given - 주문 생성 및 결제 완료
                val createOrderCommand = CreateOrderCommand(
                    userId = testUserId,
                    items = listOf(
                        OrderItemCommand(productId = product1Id, quantity = 1)
                    ),
                    couponId = null
                )
                val order = orderService.createOrder(createOrderCommand)

                val paymentCommand = ProcessPaymentCommand(userId = testUserId)
                val payment = paymentService.processPayment(order.orderId, paymentCommand)
                val transmissionId = payment.dataTransmission.transmissionId

                // when - 전송 상세 정보 조회
                val transmissionDetail = paymentService.getTransmissionDetail(transmissionId)

                // then
                transmissionDetail.transmissionId shouldBe transmissionId
                transmissionDetail.orderId shouldBe order.orderId
                transmissionDetail.status shouldBe "PENDING"
                transmissionDetail.createdAt shouldNotBe null
            }

            it("존재하지 않는 전송 ID 조회 시 예외가 발생한다") {
                // given
                val nonExistentTransmissionId = UUID.randomUUID()

                // when & then
                shouldThrow<TransmissionNotFoundException> {
                    paymentService.getTransmissionDetail(nonExistentTransmissionId)
                }
            }
        }
    }
    }
}
