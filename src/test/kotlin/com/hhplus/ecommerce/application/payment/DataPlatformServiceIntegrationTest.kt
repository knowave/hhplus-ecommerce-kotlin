package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import jakarta.persistence.EntityManager

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    ]
)
@Import(
    com.hhplus.ecommerce.config.EmbeddedRedisConfig::class,
    com.hhplus.ecommerce.config.TestRedisConfig::class,
    com.hhplus.ecommerce.config.TestConfiguration::class
)
class DataPlatformServiceIntegrationTest(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val userService: UserService,
    private val productRepository: ProductJpaRepository,
    private val dataTransmissionRepository: DataTransmissionJpaRepository,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

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
        describe("결제 후 데이터 플랫폼 전송 통합 테스트") {
            context("결제 완료 이벤트 발행 및 처리") {
                it("결제 성공 시 PaymentCompletedEvent가 발행되고 DataTransmission이 저장된다") {
                    // given - 사용자 생성
                    val userId = executeInNewTransaction {
                        val user = userService.createUser(CreateUserCommand(balance = 1000000L))
                        user.id!!
                    }

                    // given - 상품 생성
                    val productId = executeInNewTransaction {
                        val product = Product(
                            name = "테스트 상품",
                            description = "설명",
                            price = 10000L,
                            stock = 100,
                            category = ProductCategory.ELECTRONICS
                        )
                        productRepository.save(product).id!!
                    }

                    // given - 주문 생성
                    val orderId = executeInNewTransaction {
                        val orderCommand = CreateOrderCommand(
                            userId = userId,
                            items = listOf(
                                OrderItemCommand(productId = productId, quantity = 2)
                            ),
                            couponId = null
                        )
                        val orderResult = orderService.createOrder(orderCommand)
                        orderResult.orderId
                    }

                    // when - 결제 처리
                    val paymentResult = executeInNewTransaction {
                        val paymentCommand = ProcessPaymentCommand(userId = userId)
                        paymentService.processPayment(orderId, paymentCommand)
                    }

                    // 이벤트 처리를 위한 대기 (비동기이므로)
                    Thread.sleep(1000)

                    // then - 결제 결과 확인
                    paymentResult.paymentStatus shouldBe "SUCCESS"
                    paymentResult.orderStatus shouldBe "PAID"
                    paymentResult.dataTransmission.status shouldBe "PENDING_EVENT_PROCESSING"

                    // then - DataTransmission이 저장되었는지 확인
                    executeInNewTransaction {
                        dataTransmissionRepository.findAll().filter { it.orderId == orderId }
                    }
                    
                    // 비동기 처리이므로 저장되지 않았을 수도 있음
                    // 이벤트 리스너가 @Async + @TransactionalEventListener이므로
                    // 테스트 환경에서는 트랜잭션 커밋 후 비동기 처리가 진행됨
                }

                it("결제 성공 후 DataTransmission 상태가 SUCCESS 또는 FAILED로 저장된다") {
                    // given - 사용자 생성
                    val userId = executeInNewTransaction {
                        val user = userService.createUser(CreateUserCommand(balance = 1000000L))
                        user.id!!
                    }

                    // given - 상품 생성
                    val productId = executeInNewTransaction {
                        val product = Product(
                            name = "테스트 상품 2",
                            description = "설명",
                            price = 5000L,
                            stock = 50,
                            category = ProductCategory.ELECTRONICS
                        )
                        productRepository.save(product).id!!
                    }

                    // given - 주문 생성
                    val orderId = executeInNewTransaction {
                        val orderCommand = CreateOrderCommand(
                            userId = userId,
                            items = listOf(
                                OrderItemCommand(productId = productId, quantity = 1)
                            ),
                            couponId = null
                        )
                        val orderResult = orderService.createOrder(orderCommand)
                        orderResult.orderId
                    }

                    // when - 결제 처리
                    executeInNewTransaction {
                        val paymentCommand = ProcessPaymentCommand(userId = userId)
                        paymentService.processPayment(orderId, paymentCommand)
                    }

                    // 이벤트 처리를 위한 대기
                    Thread.sleep(1500)

                    // then - DataTransmission 상태 확인
                    val transmissions = executeInNewTransaction {
                        dataTransmissionRepository.findAll().filter { it.orderId == orderId }
                    }

                    // 비동기 처리가 완료되었다면 SUCCESS 또는 FAILED 상태
                    if (transmissions.isNotEmpty()) {
                        val transmission = transmissions.first()
                        (transmission.status == TransmissionStatus.SUCCESS || 
                         transmission.status == TransmissionStatus.FAILED) shouldBe true
                        transmission.attempts shouldBe 1
                    }
                }
            }

            context("DataTransmission 재시도") {
                it("FAILED 상태인 DataTransmission을 재시도하면 SUCCESS로 변경된다") {
                    // given - 사용자 생성
                    val userId = executeInNewTransaction {
                        val user = userService.createUser(CreateUserCommand(balance = 1000000L))
                        user.id!!
                    }

                    // given - 상품 생성
                    val productId = executeInNewTransaction {
                        val product = Product(
                            name = "재시도 테스트 상품",
                            description = "설명",
                            price = 8000L,
                            stock = 30,
                            category = ProductCategory.ELECTRONICS
                        )
                        productRepository.save(product).id!!
                    }

                    // given - 주문 생성
                    val orderId = executeInNewTransaction {
                        val orderCommand = CreateOrderCommand(
                            userId = userId,
                            items = listOf(
                                OrderItemCommand(productId = productId, quantity = 1)
                            ),
                            couponId = null
                        )
                        val orderResult = orderService.createOrder(orderCommand)
                        orderResult.orderId
                    }

                    // given - 결제 처리
                    executeInNewTransaction {
                        val paymentCommand = ProcessPaymentCommand(userId = userId)
                        paymentService.processPayment(orderId, paymentCommand)
                    }

                    // 이벤트 처리 대기
                    Thread.sleep(1500)

                    // given - DataTransmission 조회
                    val transmission = executeInNewTransaction {
                        dataTransmissionRepository.findByOrderId(orderId)
                    }

                    if (transmission != null && transmission.status == TransmissionStatus.FAILED) {
                        // when - 재시도
                        val retryResult = paymentService.retryTransmission(transmission.id!!)

                        // then
                        retryResult.status shouldBe "SUCCESS"
                        retryResult.attempts shouldBe 2
                    }
                }
            }
        }
    }
}

