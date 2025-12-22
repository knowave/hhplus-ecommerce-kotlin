package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.lock.LockAcquisitionFailedException
import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import java.time.LocalDateTime
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.context.EmbeddedKafka
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
@EmbeddedKafka(
    partitions = 1,
    topics = ["order-created", "payment-completed", "coupon-issued"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"]
)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-group"
    ]
)
@Import(
    com.hhplus.ecommerce.config.EmbeddedRedisConfig::class,
    com.hhplus.ecommerce.config.TestRedisConfig::class
)
class PaymentServiceIntegrationTest(
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val paymentService: PaymentService,
    private val transmissionRepository: DataTransmissionJpaRepository,
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
                result.dataTransmission.status shouldBe "PENDING_EVENT_PROCESSING"
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
                // 비동기 처리로 변경되어 transmissionId가 null일 수 있음
                result.dataTransmission.status shouldBe "PENDING_EVENT_PROCESSING"
                result.dataTransmission.scheduledAt shouldNotBe null
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
                shouldThrow<OrderForbiddenException> {
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
                paymentService.processPayment(order.orderId, paymentCommand)
                
                // @DataJpaTest에서는 @Async 이벤트 리스너가 작동하지 않으므로
                // 직접 DataTransmission 레코드를 생성하여 getTransmissionDetail 메서드 테스트
                val transmission = DataTransmission(
                    orderId = order.orderId,
                    status = TransmissionStatus.PENDING,
                    errorMessage = null,
                    attempts = 0,
                    maxAttempts = 3,
                    sentAt = null,
                    nextRetryAt = LocalDateTime.now().plusMinutes(5)
                )
                val savedTransmission = transmissionRepository.save(transmission)
                
                // when - 전송 상세 정보 조회
                val transmissionDetail = paymentService.getTransmissionDetail(savedTransmission.id!!)

                // then
                transmissionDetail.transmissionId shouldBe savedTransmission.id
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

        context("분산락 동시성 테스트 - 결제 처리") {
            it("20명의 사용자가 각각 다른 상품을 주문 후 동시에 결제하면 모두 성공한다 (락 경합 없음)") {
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

                // when - 20명이 각자의 주문을 동시에 결제 시도
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

                            // 각 스레드에서 새로운 트랜잭션으로 실행
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

            it("동일한 주문에 대해 동시에 5번 중복 결제 요청 시 1번만 성공해야 한다") {
                // given - 테스트용 사용자 및 주문 생성
                val testUser = executeInNewTransaction {
                    userService.createUser(CreateUserCommand(balance = 2000000L))
                }
                
                val order = executeInNewTransaction {
                    val createOrderCommand = CreateOrderCommand(
                        userId = testUser.id!!,
                        items = listOf(OrderItemCommand(productId = product1Id, quantity = 1)),
                        couponId = null
                    )
                    orderService.createOrder(createOrderCommand)
                }

                // when - 5개 스레드에서 동시에 같은 주문 결제 시도
                // Redis 분산락 키: payment:process:{orderId}
                val threadCount = 5
                val executorService = Executors.newFixedThreadPool(threadCount)
                val latch = CountDownLatch(threadCount)

                val successCount = AtomicInteger(0)
                val duplicatePaymentCount = AtomicInteger(0)  // AlreadyPaidException 또는 InvalidOrderStatusException
                val lockFailCount = AtomicInteger(0)
                val otherFailCount = AtomicInteger(0)

                for (i in 0 until threadCount) {
                    executorService.submit {
                        try {
                            latch.countDown()
                            latch.await()

                            // 새로운 트랜잭션으로 실행 (Spring AOP 프록시 문제 해결)
                            executeInNewTransaction {
                                val paymentCommand = ProcessPaymentCommand(userId = testUser.id!!)
                                paymentService.processPayment(order.orderId, paymentCommand)
                            }
                            successCount.incrementAndGet()
                        } catch (e: AlreadyPaidException) {
                            // 이미 결제 레코드가 존재하는 경우
                            duplicatePaymentCount.incrementAndGet()
                        } catch (e: InvalidOrderStatusException) {
                            // 주문 상태가 PENDING이 아닌 경우 (이미 PAID로 변경됨)
                            duplicatePaymentCount.incrementAndGet()
                        } catch (e: LockAcquisitionFailedException) {
                            // Redis 분산락 획득 실패 (대기 시간 초과)
                            lockFailCount.incrementAndGet()
                        } catch (e: Exception) {
                            // cause 확인
                            if (e.cause is AlreadyPaidException || e.cause is InvalidOrderStatusException) {
                                duplicatePaymentCount.incrementAndGet()
                            } else {
                                e.printStackTrace()
                                otherFailCount.incrementAndGet()
                            }
                        }
                    }
                }

                executorService.shutdown()
                while (!executorService.isTerminated) {
                    Thread.sleep(100)
                }

                // 모든 트랜잭션 커밋 완료 대기
                Thread.sleep(500)

                // then - 결과 검증
                val total = successCount.get() + duplicatePaymentCount.get() + lockFailCount.get() + otherFailCount.get()
                total shouldBe 5
                successCount.get() shouldBe 1  // 오직 1개만 성공
                // 나머지 4개는 중복 결제 예외 또는 락 획득 실패
                (duplicatePaymentCount.get() + lockFailCount.get()) shouldBe 4
                otherFailCount.get() shouldBe 0
            }
        }
    }
    }
}
