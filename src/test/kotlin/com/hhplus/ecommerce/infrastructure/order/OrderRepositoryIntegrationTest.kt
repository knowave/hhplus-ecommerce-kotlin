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

        describe("OrderRepository 통합 테스트") {
            it("findByUserIdAndStatus - 특정 유저의 특정 상태만 조회") {
                // given
                val orders = listOf(
                    Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 10000, discountAmount = 0, finalAmount = 10000, orderNumber = "ORDER-1001"),
                    Order(userId = user2Id, status = OrderStatus.PENDING, totalAmount = 5000, discountAmount = 0, finalAmount = 5000, orderNumber = "ORDER-1002"),
                )

                orders[0].items.add(OrderItem(userId = user1Id, order = orders[0], productId = product1Id, productName = product1Name, quantity = 2, unitPrice = 5000, subtotal = 10000))
                orders[1].items.add(OrderItem(userId = user2Id, order = orders[1], productId = product2Id, productName = product2Name, quantity = 1, unitPrice = 5000, subtotal = 5000))
                orderRepository.saveAll(orders)

                // when
                val result = orderRepository.findByUserIdAndStatus(user1Id, OrderStatus.PAID)

                // then
                result.size shouldBe 1
                result.first().orderNumber shouldBe "ORDER-1001"
                result.first().items.size shouldBe 1
                result.first().items.first().unitPrice shouldBe 5000
            }

            it("findByUserId - 특정 유저의 주문 조회") {
                // given
                val orders = listOf(
                    Order(userId = user1Id, status = OrderStatus.PAID, totalAmount = 10000, discountAmount = 0, finalAmount = 10000, orderNumber = "ORDER-1001"),
                    Order(userId = user2Id, status = OrderStatus.PENDING, totalAmount = 5000, discountAmount = 0, finalAmount = 5000, orderNumber = "ORDER-1002"),
                )

                orders[0].items.add(OrderItem(userId = user1Id, order = orders[0], productId = product1Id, productName = product1Name, quantity = 2, unitPrice = 5000, subtotal = 10000))
                orders[1].items.add(OrderItem(userId = user2Id, order = orders[1], productId = product2Id, productName = product2Name, quantity = 1, unitPrice = 5000, subtotal = 5000))
                orderRepository.saveAll(orders)

                // when
                val result = orderRepository.findByUserId(user1Id)

                // then
                result.size shouldBe 2
                result.map { it.orderNumber } shouldContainExactlyInAnyOrder listOf("ORDER-1001", "ORDER-1002")
            }
        }
    }
}
