package com.hhplus.ecommerce.infrastructure.order

import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.OrderRepository
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * OrderRepository 통합 테스트
 *
 * 목적: OrderRepository의 CRUD 동작을 직접 테스트
 * 특징: In-Memory Repository 구현체의 데이터 저장/조회 로직 검증
 */
class OrderRepositoryIntegrationTest : DescribeSpec({

    lateinit var orderRepository: OrderRepository

    beforeEach {
        orderRepository = OrderRepositoryImpl()
    }

    describe("OrderRepository 통합 테스트 - save & findById") {
        context("주문 저장 및 조회") {
            it("주문을 저장하고 ID로 조회할 수 있다") {
                // given
                val orderId = orderRepository.generateId()
                val orderNumber = orderRepository.generateOrderNumber(orderId)
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(
                        id = orderRepository.generateItemId(),
                        productId = 1L,
                        orderId = orderId,
                        productName = "노트북",
                        quantity = 2,
                        unitPrice = 100000L,
                        subtotal = 200000L
                    )
                )

                val order = Order(
                    id = orderId,
                    userId = 100L,
                    orderNumber = orderNumber,
                    items = orderItems,
                    totalAmount = 200000L,
                    discountAmount = 0L,
                    finalAmount = 200000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                orderRepository.save(order)
                val foundOrder = orderRepository.findById(orderId)

                // then
                foundOrder shouldNotBe null
                foundOrder!!.id shouldBe orderId
                foundOrder.userId shouldBe 100L
                foundOrder.orderNumber shouldBe orderNumber
                foundOrder.items.size shouldBe 1
                foundOrder.totalAmount shouldBe 200000L
                foundOrder.finalAmount shouldBe 200000L
                foundOrder.status shouldBe OrderStatus.PENDING
            }

            it("여러 상품을 포함한 주문을 저장하고 조회할 수 있다") {
                // given
                val orderId = orderRepository.generateId()
                val orderNumber = orderRepository.generateOrderNumber(orderId)
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(
                        id = orderRepository.generateItemId(),
                        productId = 1L,
                        orderId = orderId,
                        productName = "노트북",
                        quantity = 1,
                        unitPrice = 100000L,
                        subtotal = 100000L
                    ),
                    OrderItem(
                        id = orderRepository.generateItemId(),
                        productId = 2L,
                        orderId = orderId,
                        productName = "마우스",
                        quantity = 2,
                        unitPrice = 30000L,
                        subtotal = 60000L
                    ),
                    OrderItem(
                        id = orderRepository.generateItemId(),
                        productId = 3L,
                        orderId = orderId,
                        productName = "키보드",
                        quantity = 1,
                        unitPrice = 50000L,
                        subtotal = 50000L
                    )
                )

                val order = Order(
                    id = orderId,
                    userId = 100L,
                    orderNumber = orderNumber,
                    items = orderItems,
                    totalAmount = 210000L,
                    discountAmount = 0L,
                    finalAmount = 210000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                orderRepository.save(order)
                val foundOrder = orderRepository.findById(orderId)

                // then
                foundOrder shouldNotBe null
                foundOrder!!.items shouldHaveSize 3
                foundOrder.items[0].productName shouldBe "노트북"
                foundOrder.items[1].productName shouldBe "마우스"
                foundOrder.items[2].productName shouldBe "키보드"
                foundOrder.totalAmount shouldBe 210000L
            }

            it("쿠폰이 적용된 주문을 저장하고 조회할 수 있다") {
                // given
                val orderId = orderRepository.generateId()
                val orderNumber = orderRepository.generateOrderNumber(orderId)
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(
                        id = orderRepository.generateItemId(),
                        productId = 1L,
                        orderId = orderId,
                        productName = "노트북",
                        quantity = 1,
                        unitPrice = 100000L,
                        subtotal = 100000L
                    )
                )

                val order = Order(
                    id = orderId,
                    userId = 100L,
                    orderNumber = orderNumber,
                    items = orderItems,
                    totalAmount = 100000L,
                    discountAmount = 10000L, // 10% 할인
                    finalAmount = 90000L,
                    appliedCouponId = 1L,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                orderRepository.save(order)
                val foundOrder = orderRepository.findById(orderId)

                // then
                foundOrder shouldNotBe null
                foundOrder!!.appliedCouponId shouldBe 1L
                foundOrder.discountAmount shouldBe 10000L
                foundOrder.finalAmount shouldBe 90000L
            }

            it("주문을 업데이트할 수 있다") {
                // given - 주문 생성
                val orderId = orderRepository.generateId()
                val orderNumber = orderRepository.generateOrderNumber(orderId)
                val now = LocalDateTime.now()

                val orderItems = listOf(
                    OrderItem(
                        id = orderRepository.generateItemId(),
                        productId = 1L,
                        orderId = orderId,
                        productName = "노트북",
                        quantity = 1,
                        unitPrice = 100000L,
                        subtotal = 100000L
                    )
                )

                val order = Order(
                    id = orderId,
                    userId = 100L,
                    orderNumber = orderNumber,
                    items = orderItems,
                    totalAmount = 100000L,
                    discountAmount = 0L,
                    finalAmount = 100000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                orderRepository.save(order)

                // when - 주문 상태 변경
                order.markAsPaid()
                orderRepository.save(order)

                // then
                val updatedOrder = orderRepository.findById(orderId)
                updatedOrder shouldNotBe null
                updatedOrder!!.status shouldBe OrderStatus.PAID
            }
        }

        context("존재하지 않는 주문 조회") {
            it("존재하지 않는 ID로 조회 시 null을 반환한다") {
                // when
                val foundOrder = orderRepository.findById(999L)

                // then
                foundOrder shouldBe null
            }
        }
    }

    describe("OrderRepository 통합 테스트 - findByUserId") {
        context("사용자별 주문 조회") {
            it("특정 사용자의 모든 주문을 조회할 수 있다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()

                // 첫 번째 주문
                val order1 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 50000L,
                    status = OrderStatus.PENDING,
                    createdAt = now.minusHours(2)
                )
                orderRepository.save(order1)

                // 두 번째 주문
                val order2 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 100000L,
                    status = OrderStatus.PAID,
                    createdAt = now.minusHours(1)
                )
                orderRepository.save(order2)

                // 세 번째 주문
                val order3 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 75000L,
                    status = OrderStatus.CANCELLED,
                    createdAt = now
                )
                orderRepository.save(order3)

                // when
                val orders = orderRepository.findByUserId(userId)

                // then
                orders shouldHaveSize 3
                // 최신순 정렬 확인
                orders[0].id shouldBe order3.id
                orders[1].id shouldBe order2.id
                orders[2].id shouldBe order1.id
            }

            it("다른 사용자의 주문은 조회되지 않는다") {
                // given
                val user1Id = 100L
                val user2Id = 200L
                val now = LocalDateTime.now()

                // 사용자 1의 주문
                val order1 = createOrder(
                    orderRepository = orderRepository,
                    userId = user1Id,
                    totalAmount = 50000L,
                    status = OrderStatus.PENDING,
                    createdAt = now
                )
                orderRepository.save(order1)

                // 사용자 2의 주문
                val order2 = createOrder(
                    orderRepository = orderRepository,
                    userId = user2Id,
                    totalAmount = 100000L,
                    status = OrderStatus.PAID,
                    createdAt = now
                )
                orderRepository.save(order2)

                // when
                val user1Orders = orderRepository.findByUserId(user1Id)
                val user2Orders = orderRepository.findByUserId(user2Id)

                // then
                user1Orders shouldHaveSize 1
                user1Orders[0].userId shouldBe user1Id

                user2Orders shouldHaveSize 1
                user2Orders[0].userId shouldBe user2Id
            }

            it("주문이 없는 사용자는 빈 리스트를 반환한다") {
                // when
                val orders = orderRepository.findByUserId(999L)

                // then
                orders shouldHaveSize 0
            }
        }
    }

    describe("OrderRepository 통합 테스트 - findByUserIdAndStatus") {
        context("사용자별 상태별 주문 조회") {
            it("특정 사용자의 특정 상태 주문만 조회할 수 있다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()

                // PENDING 주문
                val pendingOrder = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 50000L,
                    status = OrderStatus.PENDING,
                    createdAt = now.minusHours(2)
                )
                orderRepository.save(pendingOrder)

                // PAID 주문 2개
                val paidOrder1 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 100000L,
                    status = OrderStatus.PAID,
                    createdAt = now.minusHours(1)
                )
                orderRepository.save(paidOrder1)

                val paidOrder2 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 75000L,
                    status = OrderStatus.PAID,
                    createdAt = now
                )
                orderRepository.save(paidOrder2)

                // CANCELLED 주문
                val cancelledOrder = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 30000L,
                    status = OrderStatus.CANCELLED,
                    createdAt = now.minusMinutes(30)
                )
                orderRepository.save(cancelledOrder)

                // when
                val pendingOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)
                val paidOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PAID)
                val cancelledOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.CANCELLED)

                // then
                pendingOrders shouldHaveSize 1
                pendingOrders[0].status shouldBe OrderStatus.PENDING

                paidOrders shouldHaveSize 2
                paidOrders.all { it.status == OrderStatus.PAID } shouldBe true

                cancelledOrders shouldHaveSize 1
                cancelledOrders[0].status shouldBe OrderStatus.CANCELLED
            }

            it("해당 상태의 주문이 없으면 빈 리스트를 반환한다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()

                // PENDING 주문만 생성
                val order = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 50000L,
                    status = OrderStatus.PENDING,
                    createdAt = now
                )
                orderRepository.save(order)

                // when - PAID 주문 조회
                val paidOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PAID)

                // then
                paidOrders shouldHaveSize 0
            }

            it("최신순으로 정렬되어 반환된다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()

                val order1 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 50000L,
                    status = OrderStatus.PAID,
                    createdAt = now.minusHours(3)
                )
                orderRepository.save(order1)

                val order2 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 60000L,
                    status = OrderStatus.PAID,
                    createdAt = now.minusHours(2)
                )
                orderRepository.save(order2)

                val order3 = createOrder(
                    orderRepository = orderRepository,
                    userId = userId,
                    totalAmount = 70000L,
                    status = OrderStatus.PAID,
                    createdAt = now.minusHours(1)
                )
                orderRepository.save(order3)

                // when
                val orders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PAID)

                // then
                orders shouldHaveSize 3
                // 최신순 확인
                orders[0].id shouldBe order3.id
                orders[1].id shouldBe order2.id
                orders[2].id shouldBe order1.id
            }
        }
    }

    describe("OrderRepository 통합 테스트 - ID 생성") {
        context("ID 생성 기능") {
            it("generateId()로 고유한 주문 ID를 생성할 수 있다") {
                // when
                val id1 = orderRepository.generateId()
                val id2 = orderRepository.generateId()
                val id3 = orderRepository.generateId()

                // then
                id1 shouldNotBe id2
                id2 shouldNotBe id3
                id1 shouldNotBe id3

                // ID가 증가하는지 확인
                id2 shouldBe id1 + 1
                id3 shouldBe id2 + 1
            }

            it("generateItemId()로 고유한 주문 아이템 ID를 생성할 수 있다") {
                // when
                val itemId1 = orderRepository.generateItemId()
                val itemId2 = orderRepository.generateItemId()
                val itemId3 = orderRepository.generateItemId()

                // then
                itemId1 shouldNotBe itemId2
                itemId2 shouldNotBe itemId3
                itemId1 shouldNotBe itemId3

                // ID가 증가하는지 확인
                itemId2 shouldBe itemId1 + 1
                itemId3 shouldBe itemId2 + 1
            }

            it("generateOrderNumber()로 형식에 맞는 주문번호를 생성할 수 있다") {
                // when
                val orderId = 1001L
                val orderNumber = orderRepository.generateOrderNumber(orderId)

                // then
                orderNumber shouldNotBe null
                orderNumber.startsWith("ORD-") shouldBe true
                orderNumber.contains("-$orderId") shouldBe true
                // 형식: ORD-YYYYMMDD-orderId
                orderNumber.split("-").size shouldBe 3
            }

            it("다른 주문 ID에 대해 다른 주문번호를 생성한다") {
                // when
                val orderNumber1 = orderRepository.generateOrderNumber(1001L)
                val orderNumber2 = orderRepository.generateOrderNumber(1002L)

                // then
                orderNumber1 shouldNotBe orderNumber2
                orderNumber1.endsWith("-1001") shouldBe true
                orderNumber2.endsWith("-1002") shouldBe true
            }
        }
    }
}) {
    companion object {
        fun createOrder(
            orderRepository: OrderRepository,
            userId: Long,
            totalAmount: Long,
            status: OrderStatus,
            createdAt: LocalDateTime
        ): Order {
            val orderId = orderRepository.generateId()
            val orderNumber = orderRepository.generateOrderNumber(orderId)

            val orderItems = listOf(
                OrderItem(
                    id = orderRepository.generateItemId(),
                    productId = 1L,
                    orderId = orderId,
                    productName = "테스트 상품",
                    quantity = 1,
                    unitPrice = totalAmount,
                    subtotal = totalAmount
                )
            )

            return Order(
                id = orderId,
                userId = userId,
                orderNumber = orderNumber,
                items = orderItems,
                totalAmount = totalAmount,
                discountAmount = 0L,
                finalAmount = totalAmount,
                appliedCouponId = null,
                status = status,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }
}
