package com.hhplus.ecommerce.infrastructure.order

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.repository.OrderRepository
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.repository.UserJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
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
class OrderRepositoryIntegrationTest(
    private val orderRepository: OrderJpaRepository,
    private val userRepository: UserJpaRepository,
    private val productRepository: ProductJpaRepository
) : DescribeSpec() {

    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID

    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID
    private lateinit var product1Name: String
    private lateinit var product2Name: String

    init {
        beforeEach {
            val user1 = userRepository.save(User( balance = 500000L ))
            val user2 = userRepository.save(User( balance = 700000L ))

            val product1 = productRepository.save(Product(
                name = "무선 이어폰 XYZ",
                description = "노이즈 캔슬링 기능이 탑재된 프리미엄 이어폰",
                price = 150000L,
                stock = 80,
                category = ProductCategory.ELECTRONICS,
                specifications = mapOf("battery" to "24 hours", "bluetooth" to "5.2", "anc" to "active"),
                salesCount = 250,
            ))
            val product2 = productRepository.save(Product(
                name = "운동화 ABC",
                description = "편안한 착용감의 러닝화",
                price = 89000L,
                stock = 45,
                category = ProductCategory.FASHION,
                specifications = mapOf("size" to "230-290mm", "material" to "mesh"),
                salesCount = 180,
            ))

            user1Id = user1.id!!
            user2Id = user2.id!!

            product1Id = product1.id!!
            product2Id = product2.id!!
            product1Name = product1.name
            product2Name = product2.name
        }

        afterEach {
            orderRepository.deleteAll()
        }

        describe("OrderJpaRepository Custom 통합 테스트 - findByUserIdAndStatus") {
            context("정상 케이스") {
                it("특정 사용자의 특정 상태 주문만 조회한다") {
                    // given - user1의 PAID 주문 1개, PENDING 주문 1개
                    val paidOrder = Order(
                        userId = user1Id,
                        status = OrderStatus.PAID,
                        totalAmount = 150000,
                        discountAmount = 0,
                        finalAmount = 150000,
                        orderNumber = "ORDER-1001"
                    )
                    paidOrder.items.add(OrderItem(
                        userId = user1Id,
                        order = paidOrder,
                        productId = product1Id,
                        productName = product1Name,
                        quantity = 1,
                        unitPrice = 150000,
                        subtotal = 150000
                    ))

                    val pendingOrder = Order(
                        userId = user1Id,
                        status = OrderStatus.PENDING,
                        totalAmount = 89000,
                        discountAmount = 0,
                        finalAmount = 89000,
                        orderNumber = "ORDER-1002"
                    )
                    pendingOrder.items.add(OrderItem(
                        userId = user1Id,
                        order = pendingOrder,
                        productId = product2Id,
                        productName = product2Name,
                        quantity = 1,
                        unitPrice = 89000,
                        subtotal = 89000
                    ))

                    orderRepository.saveAll(listOf(paidOrder, pendingOrder))

                    // when
                    val result = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.PAID)

                    // then
                    result shouldHaveSize 1
                    result[0].orderNumber shouldBe "ORDER-1001"
                    result[0].status shouldBe OrderStatus.PAID
                    result[0].userId shouldBe user1Id
                    result[0].items shouldHaveSize 1
                    result[0].items[0].unitPrice shouldBe 150000
                }

                it("다른 상태의 주문을 조회할 수 있다") {
                    // given - user1의 여러 상태 주문
                    val orders = listOf(
                        Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 100000, discountAmount = 0, finalAmount = 100000, orderNumber = "ORDER-2001"),
                        Order(userId = user1Id, status = OrderStatus.PENDING, totalAmount = 200000, discountAmount = 0, finalAmount = 200000, orderNumber = "ORDER-2002"),
                        Order(userId = user1Id, status = OrderStatus.REFUNDED, totalAmount = 300000, discountAmount = 0, finalAmount = 300000, orderNumber = "ORDER-2003")
                    )
                    orders.forEach { order ->
                        order.items.add(OrderItem(
                            userId = user1Id,
                            order = order,
                            productId = product1Id,
                            productName = product1Name,
                            quantity = 1,
                            unitPrice = order.totalAmount,
                            subtotal = order.totalAmount
                        ))
                    }
                    orderRepository.saveAll(orders)

                    // when - PENDING 상태만 조회
                    val result = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.PENDING)

                    // then
                    result shouldHaveSize 1
                    result[0].orderNumber shouldBe "ORDER-2002"
                    result[0].status shouldBe OrderStatus.PENDING
                }

                it("다른 사용자의 주문은 조회되지 않는다") {
                    // given - user1과 user2의 주문
                    val user1Order = Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 100000, discountAmount = 0, finalAmount = 100000, orderNumber = "ORDER-3001")
                    user1Order.items.add(OrderItem(userId = user1Id, order = user1Order, productId = product1Id, productName = product1Name, quantity = 1, unitPrice = 100000, subtotal = 100000))

                    val user2Order = Order(userId = user2Id, status = OrderStatus.PAID, totalAmount = 200000, discountAmount = 0, finalAmount = 200000, orderNumber = "ORDER-3002")
                    user2Order.items.add(OrderItem(userId = user2Id, order = user2Order, productId = product2Id, productName = product2Name, quantity = 1, unitPrice = 200000, subtotal = 200000))

                    orderRepository.saveAll(listOf(user1Order, user2Order))

                    // when
                    val result = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.PAID)

                    // then
                    result shouldHaveSize 1
                    result[0].orderNumber shouldBe "ORDER-3001"
                    result[0].userId shouldBe user1Id
                }
            }

            context("예외 케이스") {
                it("해당 상태의 주문이 없으면 빈 리스트를 반환한다") {
                    // given - PAID 주문만 있음
                    val paidOrder = Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 100000, discountAmount = 0, finalAmount = 100000, orderNumber = "ORDER-4001")
                    paidOrder.items.add(OrderItem(userId = user1Id, order = paidOrder, productId = product1Id, productName = product1Name, quantity = 1, unitPrice = 100000, subtotal = 100000))
                    orderRepository.save(paidOrder)

                    // when - CANCELLED 상태 조회
                    val result = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.CANCELLED)

                    // then
                    result shouldHaveSize 0
                }

                it("주문이 없는 사용자는 빈 리스트를 반환한다") {
                    // given
                    val emptyUserId = UUID.randomUUID()

                    // when
                    val result = orderRepository.findByUserIdAndStatus(emptyUserId, OrderStatus.PAID)

                    // then
                    result shouldHaveSize 0
                }
            }
        }

        describe("OrderJpaRepository Custom 메서드 테스트 - findByUserId") {
            context("정상 케이스") {
                it("특정 사용자의 모든 주문을 조회한다") {
                    // given - user1의 여러 상태 주문
                    val orders = listOf(
                        Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 100000, discountAmount = 0, finalAmount = 100000, orderNumber = "ORDER-5001"),
                        Order(userId = user1Id, status = OrderStatus.PENDING, totalAmount = 200000, discountAmount = 0, finalAmount = 200000, orderNumber = "ORDER-5002"),
                        Order(userId = user1Id, status = OrderStatus.REFUNDED, totalAmount = 300000, discountAmount = 0, finalAmount = 300000, orderNumber = "ORDER-5003")
                    )
                    orders.forEach { order ->
                        order.items.add(OrderItem(
                            userId = user1Id,
                            order = order,
                            productId = product1Id,
                            productName = product1Name,
                            quantity = 1,
                            unitPrice = order.totalAmount,
                            subtotal = order.totalAmount
                        ))
                    }
                    orderRepository.saveAll(orders)

                    // when
                    val result = orderRepository.findByUserId(user1Id)

                    // then
                    result shouldHaveSize 3
                    result.all { it.userId == user1Id } shouldBe true
                    result.map { it.orderNumber } shouldContainExactlyInAnyOrder listOf("ORDER-5001", "ORDER-5002", "ORDER-5003")
                }

                it("다른 사용자의 주문은 조회되지 않는다") {
                    // given - user1과 user2의 주문
                    val user1Orders = listOf(
                        Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 100000, discountAmount = 0, finalAmount = 100000, orderNumber = "ORDER-6001"),
                        Order(userId = user1Id, status = OrderStatus.PENDING, totalAmount = 200000, discountAmount = 0, finalAmount = 200000, orderNumber = "ORDER-6002")
                    )
                    user1Orders.forEach { order ->
                        order.items.add(OrderItem(userId = user1Id, order = order, productId = product1Id, productName = product1Name, quantity = 1, unitPrice = order.totalAmount, subtotal = order.totalAmount))
                    }

                    val user2Orders = listOf(
                        Order(userId = user2Id, status = OrderStatus.PAID, totalAmount = 300000, discountAmount = 0, finalAmount = 300000, orderNumber = "ORDER-6003"),
                        Order(userId = user2Id, status = OrderStatus.REFUNDED, totalAmount = 400000, discountAmount = 0, finalAmount = 400000, orderNumber = "ORDER-6004")
                    )
                    user2Orders.forEach { order ->
                        order.items.add(OrderItem(userId = user2Id, order = order, productId = product2Id, productName = product2Name, quantity = 1, unitPrice = order.totalAmount, subtotal = order.totalAmount))
                    }

                    orderRepository.saveAll(user1Orders + user2Orders)

                    // when
                    val result = orderRepository.findByUserId(user1Id)

                    // then
                    result shouldHaveSize 2
                    result.all { it.userId == user1Id } shouldBe true
                    result.map { it.orderNumber } shouldContainExactlyInAnyOrder listOf("ORDER-6001", "ORDER-6002")
                }

                it("주문 아이템 정보도 함께 조회된다") {
                    // given
                    val order = Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 239000, discountAmount = 0, finalAmount = 239000, orderNumber = "ORDER-7001")
                    order.items.add(OrderItem(userId = user1Id, order = order, productId = product1Id, productName = product1Name, quantity = 1, unitPrice = 150000, subtotal = 150000))
                    order.items.add(OrderItem(userId = user1Id, order = order, productId = product2Id, productName = product2Name, quantity = 1, unitPrice = 89000, subtotal = 89000))
                    orderRepository.save(order)

                    // when
                    val result = orderRepository.findByUserId(user1Id)

                    // then
                    result shouldHaveSize 1
                    result[0].items shouldHaveSize 2
                    result[0].items.map { it.productId } shouldContainExactlyInAnyOrder listOf(product1Id, product2Id)
                }
            }

            context("예외 케이스") {
                it("주문이 없는 사용자는 빈 리스트를 반환한다") {
                    // given
                    val emptyUserId = UUID.randomUUID()

                    // when
                    val result = orderRepository.findByUserId(emptyUserId)

                    // then
                    result shouldHaveSize 0
                }
            }
        }

        describe("OrderJpaRepository Custom 메서드 테스트 - 복합 시나리오") {
            context("실제 비즈니스 흐름") {
                it("주문 생성 -> 전체 조회 -> 상태별 조회") {
                    // 1. 주문 생성
                    val orders = listOf(
                        Order(userId = user1Id, status = OrderStatus.PENDING, totalAmount = 100000, discountAmount = 10000, finalAmount = 90000, orderNumber = "ORDER-8001"),
                        Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 200000, discountAmount = 0, finalAmount = 200000, orderNumber = "ORDER-8002"),
                        Order(userId = user1Id, status = OrderStatus.REFUNDED, totalAmount = 300000, discountAmount = 30000, finalAmount = 270000, orderNumber = "ORDER-8003")
                    )
                    orders.forEach { order ->
                        order.items.add(OrderItem(userId = user1Id, order = order, productId = product1Id, productName = product1Name, quantity = 1, unitPrice = order.totalAmount, subtotal = order.totalAmount))
                    }
                    orderRepository.saveAll(orders)

                    // 2. 전체 주문 조회
                    val allOrders = orderRepository.findByUserId(user1Id)
                    allOrders shouldHaveSize 3

                    // 3. PAID 상태만 조회
                    val paidOrders = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.PAID)
                    paidOrders shouldHaveSize 1
                    paidOrders[0].orderNumber shouldBe "ORDER-8002"

                    // 4. PENDING 상태만 조회
                    val pendingOrders = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.PENDING)
                    pendingOrders shouldHaveSize 1
                    pendingOrders[0].orderNumber shouldBe "ORDER-8001"
                }

                it("여러 사용자의 주문이 독립적으로 관리된다") {
                    // given
                    val user1Order = Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 100000, discountAmount = 0, finalAmount = 100000, orderNumber = "ORDER-9001")
                    user1Order.items.add(OrderItem(userId = user1Id, order = user1Order, productId = product1Id, productName = product1Name, quantity = 1, unitPrice = 100000, subtotal = 100000))

                    val user2Order = Order(userId = user2Id, status = OrderStatus.PAID, totalAmount = 200000, discountAmount = 0, finalAmount = 200000, orderNumber = "ORDER-9002")
                    user2Order.items.add(OrderItem(userId = user2Id, order = user2Order, productId = product2Id, productName = product2Name, quantity = 1, unitPrice = 200000, subtotal = 200000))

                    orderRepository.saveAll(listOf(user1Order, user2Order))

                    // when
                    val user1Orders = orderRepository.findByUserId(user1Id)
                    val user2Orders = orderRepository.findByUserId(user2Id)

                    // then
                    user1Orders shouldHaveSize 1
                    user1Orders[0].orderNumber shouldBe "ORDER-9001"

                    user2Orders shouldHaveSize 1
                    user2Orders[0].orderNumber shouldBe "ORDER-9002"
                }
            }
        }
    }
}
