package com.hhplus.ecommerce.infrastructure.shipping

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import com.hhplus.ecommerce.domain.shipping.repository.ShippingJpaRepository
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
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.util.*

/**
 * ShippingJpaRepository 통합 테스트
 * - JPA InMemory(H2) 사용
 * - Custom 메서드만 검증 (findByUserIdWithFilters, findAllByUserIdWithFilters)
 * - Order와 조인하여 userId로 조회하는 복잡한 쿼리 테스트
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
class ShippingRepositoryIntegrationTest(
    private val shippingJpaRepository: ShippingJpaRepository,
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

            // 주문 생성 (user1의 주문 3개)
            val order1 = Order(
                userId = user1Id,
                status = OrderStatus.PAID,
                totalAmount = 100000,
                discountAmount = 0,
                finalAmount = 100000,
                orderNumber = "ORDER-SHIP-001"
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
                orderNumber = "ORDER-SHIP-002"
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
                userId = user1Id,
                status = OrderStatus.PAID,
                totalAmount = 300000,
                discountAmount = 0,
                finalAmount = 300000,
                orderNumber = "ORDER-SHIP-003"
            )
            order3.items.add(OrderItem(
                userId = user1Id,
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
            shippingJpaRepository.deleteAll()
            orderJpaRepository.deleteAll()
            productJpaRepository.deleteAll()
            userJpaRepository.deleteAll()
        }

        describe("ShippingJpaRepository Custom 메서드 테스트 - findByUserIdWithFilters (페이징)") {
            context("기본 조회") {
                it("특정 사용자의 모든 배송을 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val shipping1 = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "1234567890",
                        shippingStartAt = now.minusDays(2),
                        estimatedArrivalAt = now.plusDays(1),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    val shipping2 = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "0987654321",
                        shippingStartAt = now.minusDays(1),
                        estimatedArrivalAt = now.plusDays(2),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(shipping1, shipping2))

                    val pageable = PageRequest.of(0, 10)

                    // when - 필터 없이 조회
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 2
                    page.totalElements shouldBe 2
                    page.content.all { it.orderId in listOf(order1Id, order2Id) } shouldBe true
                }

                it("다른 사용자의 배송은 조회되지 않는다") {
                    // given - user2의 주문 생성
                    val user2Order = Order(
                        userId = user2Id,
                        status = OrderStatus.PAID,
                        totalAmount = 500000,
                        discountAmount = 0,
                        finalAmount = 500000,
                        orderNumber = "ORDER-USER2-001"
                    )
                    user2Order.items.add(OrderItem(
                        userId = user2Id,
                        order = user2Order,
                        productId = product1Id,
                        productName = "테스트 상품",
                        quantity = 5,
                        unitPrice = 100000,
                        subtotal = 500000
                    ))
                    val savedUser2Order = orderJpaRepository.save(user2Order)

                    // user1과 user2의 배송 생성
                    val now = LocalDateTime.now()
                    val user1Shipping = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "1111111111",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    val user2Shipping = Shipping(
                        orderId = savedUser2Order.id!!,
                        carrier = "로젠택배",
                        trackingNumber = "2222222222",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(user1Shipping, user2Shipping))

                    val pageable = PageRequest.of(0, 10)

                    // when - user1의 배송만 조회
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 1
                    page.content[0].orderId shouldBe order1Id
                    page.content[0].trackingNumber shouldBe "1111111111"
                }

                it("배송이 생성 시간 내림차순으로 정렬된다") {
                    // given - 시간 순서대로 배송 생성
                    val now = LocalDateTime.now()
                    val shipping1 = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "FIRST",
                        shippingStartAt = now.minusDays(3),
                        estimatedArrivalAt = now.plusDays(1),
                        deliveredAt = null,
                        status = ShippingStatus.DELIVERED,
                        isDelayed = false,
                        isExpired = false
                    )
                    Thread.sleep(100) // 시간 차이를 위해 대기
                    val shipping2 = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "SECOND",
                        shippingStartAt = now.minusDays(2),
                        estimatedArrivalAt = now.plusDays(2),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    Thread.sleep(100)
                    val shipping3 = Shipping(
                        orderId = order3Id,
                        carrier = "로젠택배",
                        trackingNumber = "THIRD",
                        shippingStartAt = now.minusDays(1),
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )

                    shippingJpaRepository.save(shipping1)
                    shippingJpaRepository.save(shipping2)
                    shippingJpaRepository.save(shipping3)

                    val pageable = PageRequest.of(0, 10)

                    // when
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then - 최신 것이 먼저 (THIRD -> SECOND -> FIRST)
                    page.content shouldHaveSize 3
                    page.content[0].trackingNumber shouldBe "THIRD"
                    page.content[1].trackingNumber shouldBe "SECOND"
                    page.content[2].trackingNumber shouldBe "FIRST"
                }
            }

            context("상태 필터") {
                it("특정 상태의 배송만 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val pendingShipping = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "PENDING-001",
                        shippingStartAt = null,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )
                    val inTransitShipping = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "INTRANSIT-001",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(2),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    val deliveredShipping = Shipping(
                        orderId = order3Id,
                        carrier = "로젠택배",
                        trackingNumber = "DELIVERED-001",
                        shippingStartAt = now.minusDays(2),
                        estimatedArrivalAt = now,
                        deliveredAt = now,
                        status = ShippingStatus.DELIVERED,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(pendingShipping, inTransitShipping, deliveredShipping))

                    val pageable = PageRequest.of(0, 10)

                    // when - DELIVERED 상태만 조회
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = ShippingStatus.DELIVERED,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 1
                    page.content[0].status shouldBe ShippingStatus.DELIVERED
                    page.content[0].trackingNumber shouldBe "DELIVERED-001"
                }
            }

            context("택배사 필터") {
                it("특정 택배사의 배송만 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val cjShipping = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "CJ-001",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    val postShipping = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "POST-001",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(cjShipping, postShipping))

                    val pageable = PageRequest.of(0, 10)

                    // when - CJ대한통운만 조회
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = "CJ대한통운",
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 1
                    page.content[0].carrier shouldBe "CJ대한통운"
                    page.content[0].trackingNumber shouldBe "CJ-001"
                }
            }

            context("날짜 범위 필터") {
                it("특정 기간의 배송만 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val oldShipping = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "OLD-001",
                        shippingStartAt = now.minusDays(10),
                        estimatedArrivalAt = now.minusDays(7),
                        deliveredAt = now.minusDays(7),
                        status = ShippingStatus.DELIVERED,
                        isDelayed = false,
                        isExpired = false
                    )
                    Thread.sleep(100)
                    val recentShipping = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "RECENT-001",
                        shippingStartAt = now.minusDays(2),
                        estimatedArrivalAt = now.plusDays(1),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )

                    shippingJpaRepository.save(oldShipping)
                    shippingJpaRepository.save(recentShipping)

                    val pageable = PageRequest.of(0, 10)

                    // when - 최근 5일 내 배송만 조회
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = now.minusDays(5),
                        to = now.plusDays(1),
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 1
                    page.content[0].trackingNumber shouldBe "RECENT-001"
                }
            }

            context("복합 필터") {
                it("여러 조건을 동시에 적용한다") {
                    // given
                    val now = LocalDateTime.now()
                    val matching = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "MATCH-001",
                        shippingStartAt = now.minusDays(1),
                        estimatedArrivalAt = now.plusDays(2),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    val notMatchingStatus = Shipping(
                        orderId = order2Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "NOT-STATUS",
                        shippingStartAt = now.minusDays(1),
                        estimatedArrivalAt = now.plusDays(2),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )
                    val notMatchingCarrier = Shipping(
                        orderId = order3Id,
                        carrier = "우체국택배",
                        trackingNumber = "NOT-CARRIER",
                        shippingStartAt = now.minusDays(1),
                        estimatedArrivalAt = now.plusDays(2),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(matching, notMatchingStatus, notMatchingCarrier))

                    val pageable = PageRequest.of(0, 10)

                    // when - IN_TRANSIT 상태 + CJ대한통운
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = ShippingStatus.IN_TRANSIT,
                        carrier = "CJ대한통운",
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 1
                    page.content[0].trackingNumber shouldBe "MATCH-001"
                    page.content[0].status shouldBe ShippingStatus.IN_TRANSIT
                    page.content[0].carrier shouldBe "CJ대한통운"
                }
            }

            context("페이징") {
                it("페이지 크기에 맞게 배송을 조회한다") {
                    // given - 5개의 배송 생성
                    val now = LocalDateTime.now()
                    val shippings = (1..5).map { i ->
                        val orderId = when (i) {
                            1, 2 -> order1Id
                            3, 4 -> order2Id
                            else -> order3Id
                        }
                        Shipping(
                            orderId = orderId,
                            carrier = "CJ대한통운",
                            trackingNumber = "PAGE-$i",
                            shippingStartAt = now.minusDays(i.toLong()),
                            estimatedArrivalAt = now.plusDays(3),
                            deliveredAt = null,
                            status = ShippingStatus.IN_TRANSIT,
                            isDelayed = false,
                            isExpired = false
                        ).also { Thread.sleep(100) }
                    }
                    shippings.forEach { shippingJpaRepository.save(it) }

                    // when - 페이지 크기 2
                    val pageable = PageRequest.of(0, 2)
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 2
                    page.totalElements shouldBe 5
                    page.totalPages shouldBe 3
                    page.isFirst shouldBe true
                    page.isLast shouldBe false
                }

                it("두 번째 페이지를 조회한다") {
                    // given - 5개의 배송 생성
                    val now = LocalDateTime.now()
                    val shippings = (1..5).map { i ->
                        val orderId = when (i) {
                            1, 2 -> order1Id
                            3, 4 -> order2Id
                            else -> order3Id
                        }
                        Shipping(
                            orderId = orderId,
                            carrier = "CJ대한통운",
                            trackingNumber = "PAGE2-$i",
                            shippingStartAt = now.minusDays(i.toLong()),
                            estimatedArrivalAt = now.plusDays(3),
                            deliveredAt = null,
                            status = ShippingStatus.IN_TRANSIT,
                            isDelayed = false,
                            isExpired = false
                        ).also { Thread.sleep(100) }
                    }
                    shippings.forEach { shippingJpaRepository.save(it) }

                    // when - 두 번째 페이지
                    val pageable = PageRequest.of(1, 2)
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 2
                    page.number shouldBe 1
                    page.isFirst shouldBe false
                    page.isLast shouldBe false
                }
            }

            context("예외 케이스") {
                it("배송이 없는 사용자는 빈 페이지를 반환한다") {
                    // given
                    val emptyUserId = UUID.randomUUID()
                    val pageable = PageRequest.of(0, 10)

                    // when
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = emptyUserId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 0
                    page.totalElements shouldBe 0
                    page.isEmpty shouldBe true
                }

                it("조건에 맞는 배송이 없으면 빈 페이지를 반환한다") {
                    // given
                    val now = LocalDateTime.now()
                    val shipping = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "TEST-001",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.save(shipping)

                    val pageable = PageRequest.of(0, 10)

                    // when - 존재하지 않는 상태로 조회
                    val page = shippingJpaRepository.findByUserIdWithFilters(
                        userId = user1Id,
                        status = ShippingStatus.DELIVERED,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )

                    // then
                    page.content shouldHaveSize 0
                    page.isEmpty shouldBe true
                }
            }
        }

        describe("ShippingJpaRepository Custom 메서드 테스트 - findAllByUserIdWithFilters (페이징 없음)") {
            context("기본 조회") {
                it("특정 사용자의 모든 배송을 조회한다") {
                    // given
                    val now = LocalDateTime.now()
                    val shipping1 = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "ALL-001",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    val shipping2 = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "ALL-002",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(shipping1, shipping2))

                    // when
                    val shippings = shippingJpaRepository.findAllByUserIdWithFilters(
                        userId = user1Id,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null
                    )

                    // then
                    shippings shouldHaveSize 2
                    shippings.map { it.trackingNumber } shouldContainExactlyInAnyOrder listOf("ALL-001", "ALL-002")
                }

                it("필터 적용이 정상 동작한다") {
                    // given
                    val now = LocalDateTime.now()
                    val pendingShipping = Shipping(
                        orderId = order1Id,
                        carrier = "CJ대한통운",
                        trackingNumber = "PENDING-ALL",
                        shippingStartAt = null,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.PENDING,
                        isDelayed = false,
                        isExpired = false
                    )
                    val inTransitShipping = Shipping(
                        orderId = order2Id,
                        carrier = "우체국택배",
                        trackingNumber = "INTRANSIT-ALL",
                        shippingStartAt = now,
                        estimatedArrivalAt = now.plusDays(3),
                        deliveredAt = null,
                        status = ShippingStatus.IN_TRANSIT,
                        isDelayed = false,
                        isExpired = false
                    )
                    shippingJpaRepository.saveAll(listOf(pendingShipping, inTransitShipping))

                    // when - PENDING만 조회
                    val shippings = shippingJpaRepository.findAllByUserIdWithFilters(
                        userId = user1Id,
                        status = ShippingStatus.PENDING,
                        carrier = null,
                        from = null,
                        to = null
                    )

                    // then
                    shippings shouldHaveSize 1
                    shippings[0].trackingNumber shouldBe "PENDING-ALL"
                    shippings[0].status shouldBe ShippingStatus.PENDING
                }
            }

            context("예외 케이스") {
                it("배송이 없는 사용자는 빈 리스트를 반환한다") {
                    // given
                    val emptyUserId = UUID.randomUUID()

                    // when
                    val shippings = shippingJpaRepository.findAllByUserIdWithFilters(
                        userId = emptyUserId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null
                    )

                    // then
                    shippings shouldHaveSize 0
                }
            }
        }

        describe("ShippingJpaRepository Custom 메서드 테스트 - 복합 시나리오") {
            context("실제 비즈니스 흐름") {
                it("배송 생성 -> 조회 -> 필터링") {
                    // 1. 여러 배송 생성
                    val now = LocalDateTime.now()
                    val shippings = listOf(
                        Shipping(orderId = order1Id, carrier = "CJ대한통운", trackingNumber = "FLOW-001", shippingStartAt = now, estimatedArrivalAt = now.plusDays(3), deliveredAt = null, status = ShippingStatus.IN_TRANSIT, isDelayed = false, isExpired = false),
                        Shipping(orderId = order2Id, carrier = "우체국택배", trackingNumber = "FLOW-002", shippingStartAt = null, estimatedArrivalAt = now.plusDays(5), deliveredAt = null, status = ShippingStatus.PENDING, isDelayed = false, isExpired = false),
                        Shipping(orderId = order3Id, carrier = "CJ대한통운", trackingNumber = "FLOW-003", shippingStartAt = now.minusDays(2), estimatedArrivalAt = now, deliveredAt = now, status = ShippingStatus.DELIVERED, isDelayed = false, isExpired = false)
                    )
                    shippingJpaRepository.saveAll(shippings)

                    // 2. 전체 조회
                    val pageable = PageRequest.of(0, 10)
                    val allPage = shippingJpaRepository.findByUserIdWithFilters(user1Id, null, null, null, null, pageable)
                    allPage.content shouldHaveSize 3

                    // 3. CJ대한통운만 조회
                    val cjPage = shippingJpaRepository.findByUserIdWithFilters(user1Id, null, "CJ대한통운", null, null, pageable)
                    cjPage.content shouldHaveSize 2
                    cjPage.content.all { it.carrier == "CJ대한통운" } shouldBe true

                    // 4. 배송 완료된 것만 조회
                    val deliveredPage = shippingJpaRepository.findByUserIdWithFilters(user1Id, ShippingStatus.DELIVERED, null, null, null, pageable)
                    deliveredPage.content shouldHaveSize 1
                    deliveredPage.content[0].trackingNumber shouldBe "FLOW-003"
                }
            }
        }
    }
}