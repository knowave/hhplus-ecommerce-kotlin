package com.hhplus.ecommerce.infrastructure.payment

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
import com.hhplus.ecommerce.domain.payment.entity.Payment
import com.hhplus.ecommerce.domain.payment.entity.PaymentStatus
import com.hhplus.ecommerce.domain.payment.repository.PaymentJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.repository.UserJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.util.*

/**
 * PaymentJpaRepository 통합 테스트
 * - JPA InMemory(H2) 사용
 * - Custom 메서드만 검증 (findByOrderId)
 * - 기본 CRUD 메서드는 JpaRepository에서 제공하므로 테스트하지 않음
 */
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
class PaymentRepositoryIntegrationTest(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val productJpaRepository: ProductJpaRepository
) : DescribeSpec() {

    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private lateinit var product1Id: UUID
    private lateinit var order1Id: UUID
    private lateinit var order2Id: UUID
    private lateinit var order3Id: UUID

    init {
        beforeEach {
            // 사용자 생성
            val user1 = userJpaRepository.save(User(balance = 1000000L))
            val user2 = userJpaRepository.save(User(balance = 2000000L))
            user1Id = user1.id!!
            user2Id = user2.id!!

            // 상품 생성
            val product1 = productJpaRepository.save(Product(
                name = "테스트 상품",
                description = "테스트용",
                price = 100000L,
                stock = 100,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 0
            ))
            product1Id = product1.id!!

            // 주문 생성 (user1의 주문 2개, user2의 주문 1개)
            val order1 = Order(
                userId = user1Id,
                status = OrderStatus.PAID,
                totalAmount = 100000,
                discountAmount = 0,
                finalAmount = 100000,
                orderNumber = "ORDER-PAY-001"
            )
            order1.items.add(OrderItem(
                userId = user1Id,
                order = order1,
                productId = product1Id,
                productName = product1.name,
                quantity = 1,
                unitPrice = 100000,
                subtotal = 100000
            ))

            val order2 = Order(
                userId = user1Id,
                status = OrderStatus.PAID,
                totalAmount = 200000,
                discountAmount = 0,
                finalAmount = 200000,
                orderNumber = "ORDER-PAY-002"
            )
            order2.items.add(OrderItem(
                userId = user1Id,
                order = order2,
                productId = product1Id,
                productName = product1.name,
                quantity = 2,
                unitPrice = 100000,
                subtotal = 200000
            ))

            val order3 = Order(
                userId = user2Id,
                status = OrderStatus.PAID,
                totalAmount = 300000,
                discountAmount = 0,
                finalAmount = 300000,
                orderNumber = "ORDER-PAY-003"
            )
            order3.items.add(OrderItem(
                userId = user2Id,
                order = order3,
                productId = product1Id,
                productName = product1.name,
                quantity = 3,
                unitPrice = 100000,
                subtotal = 300000
            ))

            val savedOrder1 = orderJpaRepository.save(order1)
            val savedOrder2 = orderJpaRepository.save(order2)
            val savedOrder3 = orderJpaRepository.save(order3)

            order1Id = savedOrder1.id!!
            order2Id = savedOrder2.id!!
            order3Id = savedOrder3.id!!
        }

        afterEach {
            paymentJpaRepository.deleteAll()
            orderJpaRepository.deleteAll()
            productJpaRepository.deleteAll()
            userJpaRepository.deleteAll()
        }

        describe("PaymentJpaRepository Custom 메서드 테스트 - findByOrderId") {
            context("정상 케이스") {
                it("주문 ID로 결제 정보를 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    paymentJpaRepository.save(payment)

                    // when
                    val foundPayment = paymentJpaRepository.findByOrderId(order1Id)

                    // then
                    foundPayment shouldNotBe null
                    foundPayment!!.orderId shouldBe order1Id
                    foundPayment.userId shouldBe user1Id
                    foundPayment.amount shouldBe 100000
                    foundPayment.status shouldBe PaymentStatus.SUCCESS
                }

                it("다른 주문의 결제 정보를 정확하게 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val payment1 = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val payment2 = Payment(
                        orderId = order2Id,
                        userId = user1Id,
                        amount = 200000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    paymentJpaRepository.saveAll(listOf(payment1, payment2))

                    // when
                    val foundPayment1 = paymentJpaRepository.findByOrderId(order1Id)
                    val foundPayment2 = paymentJpaRepository.findByOrderId(order2Id)

                    // then
                    foundPayment1 shouldNotBe null
                    foundPayment1!!.orderId shouldBe order1Id
                    foundPayment1.amount shouldBe 100000

                    foundPayment2 shouldNotBe null
                    foundPayment2!!.orderId shouldBe order2Id
                    foundPayment2.amount shouldBe 200000
                }

                it("여러 결제 상태를 정확하게 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val successPayment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val failedPayment = Payment(
                        orderId = order2Id,
                        userId = user1Id,
                        amount = 200000,
                        status = PaymentStatus.FAILED,
                        paidAt = now
                    )
                    val cancelledPayment = Payment(
                        orderId = order3Id,
                        userId = user2Id,
                        amount = 300000,
                        status = PaymentStatus.CANCELLED,
                        paidAt = now
                    )
                    paymentJpaRepository.saveAll(listOf(successPayment, failedPayment, cancelledPayment))

                    // when
                    val foundSuccess = paymentJpaRepository.findByOrderId(order1Id)
                    val foundFailed = paymentJpaRepository.findByOrderId(order2Id)
                    val foundCancelled = paymentJpaRepository.findByOrderId(order3Id)

                    // then
                    foundSuccess!!.status shouldBe PaymentStatus.SUCCESS
                    foundFailed!!.status shouldBe PaymentStatus.FAILED
                    foundCancelled!!.status shouldBe PaymentStatus.CANCELLED
                }

                it("다른 사용자의 결제도 주문 ID로 조회 가능하다") {
                    // given
                    val now = LocalDateTime.now()
                    val user1Payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val user2Payment = Payment(
                        orderId = order3Id,
                        userId = user2Id,
                        amount = 300000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    paymentJpaRepository.saveAll(listOf(user1Payment, user2Payment))

                    // when
                    val foundUser1Payment = paymentJpaRepository.findByOrderId(order1Id)
                    val foundUser2Payment = paymentJpaRepository.findByOrderId(order3Id)

                    // then
                    foundUser1Payment shouldNotBe null
                    foundUser1Payment!!.userId shouldBe user1Id
                    foundUser1Payment.orderId shouldBe order1Id

                    foundUser2Payment shouldNotBe null
                    foundUser2Payment!!.userId shouldBe user2Id
                    foundUser2Payment.orderId shouldBe order3Id
                }

                it("결제 금액과 시간 정보가 정확하게 조회된다") {
                    // given
                    val paidAt = LocalDateTime.of(2025, 11, 12, 14, 30, 0)
                    val payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 123456,
                        status = PaymentStatus.SUCCESS,
                        paidAt = paidAt
                    )
                    paymentJpaRepository.save(payment)

                    // when
                    val foundPayment = paymentJpaRepository.findByOrderId(order1Id)

                    // then
                    foundPayment shouldNotBe null
                    foundPayment!!.amount shouldBe 123456
                    foundPayment.paidAt shouldBe paidAt
                }
            }

            context("예외 케이스") {
                it("존재하지 않는 주문 ID로 조회하면 null을 반환한다") {
                    // given
                    val nonExistentOrderId = UUID.randomUUID()

                    // when
                    val foundPayment = paymentJpaRepository.findByOrderId(nonExistentOrderId)

                    // then
                    foundPayment shouldBe null
                }

                it("결제가 생성되지 않은 주문 ID로 조회하면 null을 반환한다") {
                    // given - order1에 대한 결제만 생성
                    val now = LocalDateTime.now()
                    val payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    paymentJpaRepository.save(payment)

                    // when - order2에 대한 결제 조회 (없음)
                    val foundPayment = paymentJpaRepository.findByOrderId(order2Id)

                    // then
                    foundPayment shouldBe null
                }

                it("삭제된 결제는 조회되지 않는다") {
                    // given
                    val now = LocalDateTime.now()
                    val payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val savedPayment = paymentJpaRepository.save(payment)

                    // 결제 삭제
                    paymentJpaRepository.delete(savedPayment)

                    // when
                    val foundPayment = paymentJpaRepository.findByOrderId(order1Id)

                    // then
                    foundPayment shouldBe null
                }
            }
        }

        describe("PaymentJpaRepository Custom 메서드 테스트 - 복합 시나리오") {
            context("실제 비즈니스 흐름") {
                it("주문 생성 -> 결제 생성 -> 결제 조회") {
                    // 1. 주문이 이미 생성되어 있음 (beforeEach에서)
                    val order = orderJpaRepository.findById(order1Id)
                    order.isPresent shouldBe true
                    order.get().status shouldBe OrderStatus.PAID

                    // 2. 결제 생성
                    val now = LocalDateTime.now()
                    val payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = order.get().finalAmount,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val savedPayment = paymentJpaRepository.save(payment)
                    savedPayment.id shouldNotBe null

                    // 3. 결제 조회
                    val foundPayment = paymentJpaRepository.findByOrderId(order1Id)
                    foundPayment shouldNotBe null
                    foundPayment!!.amount shouldBe order.get().finalAmount
                    foundPayment.status shouldBe PaymentStatus.SUCCESS
                }

                it("여러 주문의 결제를 독립적으로 관리한다") {
                    // given - 3개의 주문에 대한 결제 생성
                    val now = LocalDateTime.now()
                    val payments = listOf(
                        Payment(orderId = order1Id, userId = user1Id, amount = 100000, status = PaymentStatus.SUCCESS, paidAt = now),
                        Payment(orderId = order2Id, userId = user1Id, amount = 200000, status = PaymentStatus.SUCCESS, paidAt = now.minusHours(1)),
                        Payment(orderId = order3Id, userId = user2Id, amount = 300000, status = PaymentStatus.SUCCESS, paidAt = now.minusHours(2))
                    )
                    paymentJpaRepository.saveAll(payments)

                    // when - 각 주문의 결제 조회
                    val payment1 = paymentJpaRepository.findByOrderId(order1Id)
                    val payment2 = paymentJpaRepository.findByOrderId(order2Id)
                    val payment3 = paymentJpaRepository.findByOrderId(order3Id)

                    // then - 각 결제가 독립적으로 조회됨
                    payment1 shouldNotBe null
                    payment1!!.amount shouldBe 100000
                    payment1.userId shouldBe user1Id

                    payment2 shouldNotBe null
                    payment2!!.amount shouldBe 200000
                    payment2.userId shouldBe user1Id

                    payment3 shouldNotBe null
                    payment3!!.amount shouldBe 300000
                    payment3.userId shouldBe user2Id
                }

                it("결제 실패 후 재결제 시나리오") {
                    // 1. 첫 번째 결제 시도 실패
                    val now = LocalDateTime.now()
                    val failedPayment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.FAILED,
                        paidAt = now
                    )
                    paymentJpaRepository.save(failedPayment)

                    // 2. 실패한 결제 조회
                    val foundFailed = paymentJpaRepository.findByOrderId(order1Id)
                    foundFailed shouldNotBe null
                    foundFailed!!.status shouldBe PaymentStatus.FAILED

                    // 3. 결제 상태 업데이트 (재결제 성공)
                    foundFailed.status = PaymentStatus.SUCCESS
                    paymentJpaRepository.save(foundFailed)

                    // 4. 업데이트된 결제 조회
                    val foundSuccess = paymentJpaRepository.findByOrderId(order1Id)
                    foundSuccess shouldNotBe null
                    foundSuccess!!.status shouldBe PaymentStatus.SUCCESS
                    foundSuccess.id shouldBe foundFailed.id // 같은 결제 엔티티
                }

                it("결제 취소 시나리오") {
                    // 1. 결제 성공
                    val now = LocalDateTime.now()
                    val payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val savedPayment = paymentJpaRepository.save(payment)

                    // 2. 결제 조회 및 상태 확인
                    val foundPayment = paymentJpaRepository.findByOrderId(order1Id)
                    foundPayment shouldNotBe null
                    foundPayment!!.status shouldBe PaymentStatus.SUCCESS

                    // 3. 결제 취소
                    foundPayment.status = PaymentStatus.CANCELLED
                    paymentJpaRepository.save(foundPayment)

                    // 4. 취소된 결제 조회
                    val cancelledPayment = paymentJpaRepository.findByOrderId(order1Id)
                    cancelledPayment shouldNotBe null
                    cancelledPayment!!.status shouldBe PaymentStatus.CANCELLED
                    cancelledPayment.id shouldBe savedPayment.id
                }

                it("여러 사용자의 동시 결제가 독립적으로 처리된다") {
                    // given - 같은 시간에 여러 사용자가 결제
                    val now = LocalDateTime.now()
                    val user1Payment = Payment(
                        orderId = order1Id,
                        userId = user1Id,
                        amount = 100000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    val user2Payment = Payment(
                        orderId = order3Id,
                        userId = user2Id,
                        amount = 300000,
                        status = PaymentStatus.SUCCESS,
                        paidAt = now
                    )
                    paymentJpaRepository.saveAll(listOf(user1Payment, user2Payment))

                    // when - 각 사용자의 주문별 결제 조회
                    val foundUser1Payment = paymentJpaRepository.findByOrderId(order1Id)
                    val foundUser2Payment = paymentJpaRepository.findByOrderId(order3Id)

                    // then - 각 결제가 독립적으로 관리됨
                    foundUser1Payment shouldNotBe null
                    foundUser1Payment!!.userId shouldBe user1Id
                    foundUser1Payment.amount shouldBe 100000

                    foundUser2Payment shouldNotBe null
                    foundUser2Payment!!.userId shouldBe user2Id
                    foundUser2Payment.amount shouldBe 300000

                    // 서로 영향을 주지 않음
                    foundUser1Payment.id shouldNotBe foundUser2Payment.id
                }
            }
        }
    }
}