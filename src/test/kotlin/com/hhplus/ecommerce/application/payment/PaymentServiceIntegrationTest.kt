package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import jakarta.persistence.EntityManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val paymentService: PaymentService,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testUserId: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID

    // 새로운 트랜잭션에서 실행하고 커밋하는 헬퍼 메서드
    private fun <T> executeInNewTransaction(action: () -> T): T {
        val definition = DefaultTransactionDefinition().apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        val status = transactionManager.getTransaction(definition)
        return try {
            val result = action()
            entityManager.flush()
            transactionManager.commit(status)
            result
        } catch (e: Exception) {
            transactionManager.rollback(status)
            throw e
        }
    }

    init {
        beforeSpec {
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
            // @DataJpaTest 자동 롤백으로 테스트 격리
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

        context("비관적 락 동시성 테스트 - 결제 처리") {
            it("20명의 사용자가 각각 다른 상품을 주문 후 동시에 결제하면 모두 성공한다") {
                // given - 각 사용자별로 충분한 재고를 가진 상품 20개 생성
                val testProducts = mutableListOf<UUID>()
                repeat(20) { index ->
                    val testProduct = executeInNewTransaction {
                        val product = Product(
                            name = "Concurrent Test Product $index",
                            description = "Stock 1 - For User $index",
                            price = 50000L,
                            stock = 1,
                            category = ProductCategory.ELECTRONICS,
                            specifications = emptyMap(),
                            salesCount = 0
                        )
                        val saved = productService.updateProduct(product)
                        saved.id!!
                    }
                    testProducts.add(testProduct)
                }

                // given - 20명 사용자와 각각의 주문 생성 (각자 다른 상품)
                val userCount = 20
                val userOrderPairs = mutableListOf<Pair<UUID, UUID>>()
                
                repeat(userCount) { index ->
                    val user = executeInNewTransaction {
                        userService.createUser(CreateUserCommand(balance = 2000000L))
                    }
                    
                    val order = executeInNewTransaction {
                        val createOrderCommand = CreateOrderCommand(
                            userId = user.id!!,
                            items = listOf(OrderItemCommand(productId = testProducts[index], quantity = 1)),
                            couponId = null
                        )
                        orderService.createOrder(createOrderCommand)
                    }
                    
                    userOrderPairs.add(Pair(user.id!!, order.orderId))
                }

                // when - 20명이 각자의 주문을 동시에 결제 시도 (비관적 락 테스트)
                val threadCount = 20
                val executorService = Executors.newFixedThreadPool(threadCount)
                val latch = CountDownLatch(threadCount)

                val successCount = AtomicInteger(0)
                val failCount = AtomicInteger(0)
                val exceptions = mutableListOf<Exception>()

                for (i in 0 until threadCount) {
                    val (userId, orderId) = userOrderPairs[i]
                    
                    executorService.submit {
                        try {
                            latch.countDown()
                            latch.await() // 모든 스레드가 준비될 때까지 대기

                            // 각 스레드에서 새로운 트랜잭션으로 실행 (비관적 락 테스트)
                            executeInNewTransaction {
                                val paymentCommand = ProcessPaymentCommand(userId = userId)
                                paymentService.processPayment(orderId, paymentCommand)
                            }

                            successCount.incrementAndGet()
                        } catch (e: Exception) {
                            println("Exception in thread $i: ${e::class.simpleName} - ${e.message}")
                            exceptions.add(e)
                            failCount.incrementAndGet()
                        }
                    }
                }

                executorService.shutdown()
                while (!executorService.isTerminated) {
                    Thread.sleep(100)
                }

                // then - 결과 검증
                (successCount.get() + failCount.get()) shouldBe 20
                successCount.get() shouldBe 20  // 모든 사용자가 자신의 주문 결제 성공
                failCount.get() shouldBe 0  // 실패 없음
            }

            it("비관적락으로 동시 주문 생성 제어 - 같은 상품, 제한된 재고(10개), 20명 동시 주문") {
                // given - 재고 10개인 상품 생성
                val testProduct = executeInNewTransaction {
                    val product = Product(
                        name = "Limited Stock Test Product",
                        description = "Stock 10 - Only 10 users can create order",
                        price = 50000L,
                        stock = 10,
                        category = ProductCategory.ELECTRONICS,
                        specifications = emptyMap(),
                        salesCount = 0
                    )
                    val saved = productService.updateProduct(product)
                    saved.id!!
                }

                // when - 20명이 동시에 같은 상품의 주문 생성 시도 (비관적락 테스트)
                // 각 스레드가 독립적인 트랜잭션에서 주문 생성을 시도
                val userCount = 20
                val executorService = Executors.newFixedThreadPool(userCount)
                val latch = CountDownLatch(userCount)

                val successCount = AtomicInteger(0)
                val insufficientStockCount = AtomicInteger(0)
                val otherFailCount = AtomicInteger(0)

                for (i in 0 until userCount) {
                    executorService.submit {
                        try {
                            latch.countDown()
                            latch.await() // 모든 스레드가 준비될 때까지 대기

                            // 각 스레드에서 새로운 트랜잭션으로 실행
                            executeInNewTransaction {
                                val user = userService.createUser(CreateUserCommand(balance = 2000000L))
                                val createOrderCommand = CreateOrderCommand(
                                    userId = user.id!!,
                                    items = listOf(OrderItemCommand(productId = testProduct, quantity = 1)),
                                    couponId = null
                                )
                                orderService.createOrder(createOrderCommand)
                            }

                            successCount.incrementAndGet()
                        } catch (e: InsufficientStockException) {
                            // 비관적 락에 의해 재고 부족 예외 발생 (정상 동작)
                            insufficientStockCount.incrementAndGet()
                        } catch (e: Exception) {
                            println("Unexpected exception in thread $i: ${e::class.simpleName} - ${e.message}")
                            e.printStackTrace()
                            otherFailCount.incrementAndGet()
                        }
                    }
                }

                executorService.shutdown()
                while (!executorService.isTerminated) {
                    Thread.sleep(100)
                }

                // then
                (successCount.get() + insufficientStockCount.get() + otherFailCount.get()) shouldBe 20
                successCount.get() shouldBe 10  // 재고 10개이므로 정확히 10명만 주문 생성 성공
                insufficientStockCount.get() shouldBe 10  // 나머지 10명은 비관적 락에 의해 재고부족 예외
                otherFailCount.get() shouldBe 0  // 예상치 못한 예외는 없음
            }
        }
    }
    }
}
