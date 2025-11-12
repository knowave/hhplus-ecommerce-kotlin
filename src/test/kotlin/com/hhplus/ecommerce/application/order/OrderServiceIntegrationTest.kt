package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.application.order.dto.*
import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
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
import java.time.LocalDateTime
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
class OrderServiceIntegrationTest(
    private val orderRepository: OrderJpaRepository,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val userService: UserService,
    private val orderService: OrderService,
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testUserId: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID
    private lateinit var couponId: UUID

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
        beforeEach {
            // 사용자 생성
            val createUserCommand = CreateUserCommand(balance = 500000L)
            val savedUser = userService.createUser(createUserCommand)
            testUserId = savedUser.id!!

            // 상품 생성
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

            // 쿠폰 생성
            val coupon = Coupon(
                name = "10% 할인 쿠폰",
                description = "전품목 10% 할인",
                discountRate = 10,
                totalQuantity = 100,
                issuedQuantity = 0,
                startDate = LocalDateTime.now(),
                endDate = LocalDateTime.now().plusDays(30),
                validityDays = 30
            )
            val savedCoupon = couponRepository.save(coupon)
            couponId = savedCoupon.id!!
        }

        afterEach {
            // 테스트 데이터 정리
            userCouponRepository.deleteAll()
            orderRepository.deleteAll()
        }

        describe("OrderService 통합 테스트 - 주문 전체 플로우") {

            context("주문 생성 및 조회 통합 시나리오") {
                it("사용자가 상품을 주문하고 조회할 수 있다") {
                    // given
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 2)),
                        couponId = null
                    )

                    // when - 주문 생성
                    val orderResponse = orderService.createOrder(command)

                    // then - 주문 생성 검증
                    orderResponse.orderId shouldNotBe null
                    orderResponse.userId shouldBe testUserId
                    orderResponse.items.size shouldBe 1
                    orderResponse.pricing.totalAmount shouldBe 200000L
                    orderResponse.pricing.finalAmount shouldBe 200000L
                    orderResponse.status shouldBe "PENDING"

                    // when - 주문 조회
                    val orderDetail = orderService.getOrderDetail(orderResponse.orderId, testUserId)

                    // then - 주문 상세 정보 검증
                    orderDetail.orderId shouldBe orderResponse.orderId
                    orderDetail.userId shouldBe testUserId
                    orderDetail.status shouldBe "PENDING"
                    orderDetail.pricing.totalAmount shouldBe 200000L

                    // then - 재고 차감 검증
                    val updatedProduct = productService.findProductById(product1Id)
                    updatedProduct.stock shouldBe 8 // 10 - 2 = 8
                }

                it("쿠폰을 사용하여 주문할 수 있다") {
                    // given - 쿠폰 발급
                    val issueCouponCommand = IssueCouponCommand(userId = testUserId)
                    couponService.issueCoupon(couponId, issueCouponCommand)

                    // given - 주문 생성
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 1)),
                        couponId = couponId
                    )

                    // when
                    val orderResponse = orderService.createOrder(command)

                    // then
                    orderResponse.pricing.totalAmount shouldBe 100000L
                    orderResponse.pricing.discountAmount shouldBe 10000L // 10% 할인
                    orderResponse.pricing.finalAmount shouldBe 90000L
                    orderResponse.pricing.appliedCoupon shouldNotBe null
                    orderResponse.pricing.appliedCoupon?.couponId shouldBe couponId

                    // then - 쿠폰 사용 처리 검증
                    val userCoupon = couponService.getUserCoupons(testUserId, CouponStatus.USED)
                    userCoupon.coupons.size shouldBe 1
                    userCoupon.coupons[0].status shouldBe "USED"
                }

                it("여러 상품을 한 번에 주문할 수 있다") {
                    // given
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(
                            OrderItemCommand(product1Id, 1),
                            OrderItemCommand(product2Id, 2)
                        ),
                        couponId = null
                    )

                    // when
                    val orderResponse = orderService.createOrder(command)

                    // then
                    orderResponse.items.size shouldBe 2
                    orderResponse.pricing.totalAmount shouldBe 160000L // 100000 + (30000 * 2)
                    orderResponse.pricing.finalAmount shouldBe 160000L

                    // then - 각 상품의 재고 차감 검증
                    val updatedProduct1 = productService.findProductById(product1Id)
                    val updatedProduct2 = productService.findProductById(product2Id)
                    updatedProduct1.stock shouldBe 9 // 10 - 1
                    updatedProduct2.stock shouldBe 18 // 20 - 2
                }
            }

            context("주문 취소") {
                it("주문을 취소하면 재고가 복원된다") {
                    // given - 주문 생성
                    val createCommand = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 3)),
                        couponId = null
                    )
                    val orderResponse = orderService.createOrder(createCommand)

                    // 재고 차감 확인
                    val productAfterOrder = productService.findProductById(product1Id)
                    productAfterOrder.stock shouldBe 7 // 10 - 3

                    // when - 주문 취소
                    val cancelCommand = CancelOrderCommand(testUserId)
                    val cancelResponse = orderService.cancelOrder(orderResponse.orderId, cancelCommand)

                    // then
                    cancelResponse.status shouldBe "CANCELLED"
                    cancelResponse.refund.restoredStock.size shouldBe 1
                    cancelResponse.refund.restoredStock[0].productId shouldBe product1Id
                    cancelResponse.refund.restoredStock[0].quantity shouldBe 3

                    // then - 재고 복원 검증
                    val productAfterCancel = productService.findProductById(product1Id)
                    productAfterCancel.stock shouldBe 10 // 7 + 3 = 10 (원래대로 복원)
                }

                it("쿠폰 사용한 주문을 취소하면 쿠폰이 복원된다") {
                    // given - 쿠폰 발급
                    val issueCouponCommand = IssueCouponCommand(userId = testUserId)
                    val issuedCoupon = couponService.issueCoupon(couponId, issueCouponCommand)

                    // given - 쿠폰 사용하여 주문 생성
                    val createCommand = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 1)),
                        couponId = couponId
                    )
                    val orderResponse = orderService.createOrder(createCommand)

                    // 쿠폰 사용됨 확인
                    val usedCoupons = couponService.getUserCoupons(testUserId, CouponStatus.USED)
                    usedCoupons.coupons.size shouldBe 1

                    // when - 주문 취소
                    val cancelCommand = CancelOrderCommand(testUserId)
                    val cancelResponse = orderService.cancelOrder(orderResponse.orderId, cancelCommand)

                    // then
                    cancelResponse.status shouldBe "CANCELLED"
                    cancelResponse.refund.restoredCoupon shouldNotBe null
                    cancelResponse.refund.restoredCoupon?.couponId shouldBe couponId

                    // then - 쿠폰 복원 검증
                    val availableCoupons = couponService.getUserCoupons(testUserId, CouponStatus.AVAILABLE)
                    availableCoupons.coupons.size shouldBe 1
                    availableCoupons.coupons[0].status shouldBe "AVAILABLE"
                }
            }

            context("재고 부족 예외") {
                it("재고가 부족한 경우 주문이 실패한다") {
                    // given
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 100)), // 재고 10개인데 100개 주문
                        couponId = null
                    )

                    // when & then
                    shouldThrow<InsufficientStockException> {
                        orderService.createOrder(command)
                    }

                    // then - 재고가 차감되지 않았는지 확인
                    val product = productService.findProductById(product1Id)
                    product.stock shouldBe 10 // 변화 없음
                }
            }

            context("동시성 테스트 - 주문 생성") {
                it("10개 재고를 20명이 동시에 1개씩 주문하면 정확히 10명만 성공한다") {
                    // given - 재고 10개인 상품 생성
                    val productId = executeInNewTransaction {
                        val product = Product(
                            name = "동시성 테스트 상품",
                            description = "재고 10개",
                            price = 10000L,
                            stock = 10,
                            category = ProductCategory.ELECTRONICS,
                            specifications = emptyMap(),
                            salesCount = 0
                        )
                        val saved = productService.updateProduct(product)
                        saved.id!!
                    }

                    // given - 20명의 사용자 생성
                    val userCount = 20
                    val userIds = mutableListOf<UUID>()

                    repeat(userCount) {
                        val userId = executeInNewTransaction {
                            val user = userService.createUser(CreateUserCommand(balance = 100000L))
                            user.id!!
                        }
                        userIds.add(userId)
                    }

                    // when - 20명이 동시에 1개씩 주문
                    val threadCount = 20
                    val executorService = Executors.newFixedThreadPool(threadCount)
                    val latch = CountDownLatch(threadCount)

                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    for (i in 0 until threadCount) {
                        val userId = userIds[i]
                        executorService.submit {
                            try {
                                latch.countDown()
                                latch.await() // 모든 스레드가 준비될 때까지 대기

                                val command = CreateOrderCommand(
                                    userId = userId,
                                    items = listOf(OrderItemCommand(productId, 1)),
                                    couponId = null
                                )
                                orderService.createOrder(command)

                                successCount.incrementAndGet()
                            } catch (e: InsufficientStockException) {
                                // 재고 부족 예외는 정상 동작
                                failCount.incrementAndGet()
                            } catch (e: Exception) {
                                // 그 외 예외는 실패로 처리
                                println("Unexpected exception: ${e.message}")
                                e.printStackTrace()
                                failCount.incrementAndGet()
                            }
                        }
                    }

                    executorService.shutdown()
                    while (!executorService.isTerminated) {
                        Thread.sleep(100)
                    }

                    // then - 재고 검증 먼저 확인
                    val updatedProduct = productService.findProductById(productId)
                    println("=== 주문 동시성 테스트 결과 ===")
                    println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
                    println("최종 재고: ${updatedProduct.stock}")

                    // then - 성공/실패 개수 검증
                    updatedProduct.stock shouldBe 0 // 10 - 10 = 0
                    successCount.get() shouldBe 10
                    failCount.get() shouldBe 10
                }

                it("5개 재고를 10명이 동시에 1개씩 주문하면 정확히 5명만 성공한다") {
                    // given - 재고 5개인 상품 생성
                    val productId = executeInNewTransaction {
                        val product = Product(
                            name = "동시성 테스트 상품2",
                            description = "재고 5개",
                            price = 20000L,
                            stock = 5,
                            category = ProductCategory.ELECTRONICS,
                            specifications = emptyMap(),
                            salesCount = 0
                        )
                        val saved = productService.updateProduct(product)
                        saved.id!!
                    }

                    // given - 10명의 사용자 생성
                    val userCount = 10
                    val userIds = mutableListOf<UUID>()

                    repeat(userCount) {
                        val userId = executeInNewTransaction {
                            val user = userService.createUser(CreateUserCommand(balance = 100000L))
                            user.id!!
                        }
                        userIds.add(userId)
                    }

                    // when - 10명이 동시에 1개씩 주문
                    val threadCount = 10
                    val executorService = Executors.newFixedThreadPool(threadCount)
                    val latch = CountDownLatch(threadCount)

                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    for (i in 0 until threadCount) {
                        val userId = userIds[i]
                        executorService.submit {
                            try {
                                latch.countDown()
                                latch.await()

                                val command = CreateOrderCommand(
                                    userId = userId,
                                    items = listOf(OrderItemCommand(productId, 1)),
                                    couponId = null
                                )
                                orderService.createOrder(command)

                                successCount.incrementAndGet()
                            } catch (e: InsufficientStockException) {
                                failCount.incrementAndGet()
                            } catch (e: Exception) {
                                println("Unexpected exception: ${e.message}")
                                e.printStackTrace()
                                failCount.incrementAndGet()
                            }
                        }
                    }

                    executorService.shutdown()
                    while (!executorService.isTerminated) {
                        Thread.sleep(100)
                    }

                    // then - 재고 검증 먼저 확인
                    val updatedProduct = productService.findProductById(productId)
                    println("=== 주문 동시성 테스트 결과 ===")
                    println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
                    println("최종 재고: ${updatedProduct.stock}")

                    // then - 성공/실패 개수 검증
                    updatedProduct.stock shouldBe 0 // 5 - 5 = 0
                    successCount.get() shouldBe 5
                    failCount.get() shouldBe 5
                }
            }
        }
    }
}
