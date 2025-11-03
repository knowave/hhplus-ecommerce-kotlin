package com.hhplus.ecommerce.model.order

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

class OrderUnitTest : DescribeSpec({

    describe("Order 도메인 모델 단위 테스트") {
        context("Order 객체 생성") {
            it("Order 객체가 정상적으로 생성된다") {
                // given
                val orderId = 1L
                val userId = 100L
                val orderNumber = "ORD-20251103-000001"
                val items = listOf(
                    OrderItem(1L, 1L, "노트북", 1, 1500000L, 1500000L),
                    OrderItem(2L, 2L, "마우스", 2, 30000L, 60000L)
                )
                val totalAmount = 1560000L
                val discountAmount = 156000L
                val finalAmount = 1404000L
                val appliedCouponId = 1L
                val status = OrderStatus.PENDING
                val now = LocalDateTime.now()

                // when
                val order = Order(
                    id = orderId,
                    userId = userId,
                    orderNumber = orderNumber,
                    items = items,
                    totalAmount = totalAmount,
                    discountAmount = discountAmount,
                    finalAmount = finalAmount,
                    appliedCouponId = appliedCouponId,
                    status = status,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                order shouldNotBe null
                order.id shouldBe orderId
                order.userId shouldBe userId
                order.orderNumber shouldBe orderNumber
                order.items shouldBe items
                order.totalAmount shouldBe totalAmount
                order.discountAmount shouldBe discountAmount
                order.finalAmount shouldBe finalAmount
                order.appliedCouponId shouldBe appliedCouponId
                order.status shouldBe status
                order.createdAt shouldBe now
                order.updatedAt shouldBe now
            }

            it("쿠폰을 사용하지 않은 주문을 생성할 수 있다 (appliedCouponId = null)") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))

                // when
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 10000L,
                    discountAmount = 0L,
                    finalAmount = 10000L,
                    appliedCouponId = null, // 쿠폰 없음
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                order.appliedCouponId shouldBe null
                order.discountAmount shouldBe 0L
                order.finalAmount shouldBe order.totalAmount
            }

            it("여러 개의 OrderItem을 포함하는 Order를 생성할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(
                    OrderItem(1L, 1L, "상품1", 2, 10000L, 20000L),
                    OrderItem(2L, 2L, "상품2", 1, 30000L, 30000L),
                    OrderItem(3L, 3L, "상품3", 3, 5000L, 15000L)
                )

                // when
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 65000L,
                    discountAmount = 0L,
                    finalAmount = 65000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                order.items.size shouldBe 3
                order.totalAmount shouldBe 65000L
            }
        }

        context("Order 속성 변경") {
            it("Order의 상태를 PENDING에서 PAID로 변경할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 10000L,
                    discountAmount = 0L,
                    finalAmount = 10000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when - 결제 완료
                order.status = OrderStatus.PAID
                order.updatedAt = LocalDateTime.now()

                // then
                order.status shouldBe OrderStatus.PAID
                order.updatedAt shouldNotBe now
            }

            it("Order의 상태를 PENDING에서 CANCELLED로 변경할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 10000L,
                    discountAmount = 0L,
                    finalAmount = 10000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when - 주문 취소
                order.status = OrderStatus.CANCELLED
                order.updatedAt = LocalDateTime.now()

                // then
                order.status shouldBe OrderStatus.CANCELLED
            }

            it("Order의 updatedAt을 변경할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 10000L,
                    discountAmount = 0L,
                    finalAmount = 10000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                Thread.sleep(10)
                val newUpdatedAt = LocalDateTime.now()
                order.updatedAt = newUpdatedAt

                // then
                order.updatedAt shouldNotBe now
                order.updatedAt shouldBe newUpdatedAt
            }
        }

        context("OrderStatus 열거형 동작") {
            it("모든 주문 상태를 조회할 수 있다") {
                // when
                val statuses = OrderStatus.entries

                // then
                statuses.size shouldBe 3
                statuses shouldBe listOf(
                    OrderStatus.PENDING,
                    OrderStatus.PAID,
                    OrderStatus.CANCELLED
                )
            }

            it("문자열로 OrderStatus를 생성할 수 있다") {
                // when
                val pending = OrderStatus.valueOf("PENDING")
                val paid = OrderStatus.valueOf("PAID")
                val cancelled = OrderStatus.valueOf("CANCELLED")

                // then
                pending shouldBe OrderStatus.PENDING
                paid shouldBe OrderStatus.PAID
                cancelled shouldBe OrderStatus.CANCELLED
            }
        }

        context("비즈니스 시나리오 테스트") {
            it("주문 생성 시 총 금액이 정확하게 계산된다") {
                // given
                val items = listOf(
                    OrderItem(1L, 1L, "상품1", 2, 10000L, 20000L),
                    OrderItem(2L, 2L, "상품2", 1, 30000L, 30000L)
                )
                val totalAmount = items.sumOf { it.subtotal }

                // when
                val now = LocalDateTime.now()
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = totalAmount,
                    discountAmount = 0L,
                    finalAmount = totalAmount,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                order.totalAmount shouldBe 50000L
                order.finalAmount shouldBe 50000L
            }

            it("쿠폰 할인이 적용된 주문의 금액 계산이 정확하다") {
                // given
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 100000L, 100000L))
                val totalAmount = 100000L
                val discountRate = 10 // 10%
                val discountAmount = totalAmount * discountRate / 100
                val finalAmount = totalAmount - discountAmount

                // when
                val now = LocalDateTime.now()
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = totalAmount,
                    discountAmount = discountAmount,
                    finalAmount = finalAmount,
                    appliedCouponId = 1L,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                order.totalAmount shouldBe 100000L
                order.discountAmount shouldBe 10000L
                order.finalAmount shouldBe 90000L
                order.appliedCouponId shouldBe 1L
            }

            it("결제 완료 시나리오: PENDING → PAID") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 10000L,
                    discountAmount = 0L,
                    finalAmount = 10000L,
                    appliedCouponId = null,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when - 결제 성공
                order.status = OrderStatus.PAID
                order.updatedAt = LocalDateTime.now()

                // then
                order.status shouldBe OrderStatus.PAID
            }

            it("주문 취소 시나리오: PENDING → CANCELLED") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order = Order(
                    id = 1L,
                    userId = 100L,
                    orderNumber = "ORD-20251103-000001",
                    items = items,
                    totalAmount = 10000L,
                    discountAmount = 1000L,
                    finalAmount = 9000L,
                    appliedCouponId = 1L,
                    status = OrderStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // when - 주문 취소 (재고 복원, 쿠폰 복원)
                order.status = OrderStatus.CANCELLED
                order.updatedAt = LocalDateTime.now()

                // then
                order.status shouldBe OrderStatus.CANCELLED
            }

            it("주문 번호 형식을 검증할 수 있다") {
                // given
                val orderNumber = "ORD-20251103-000001"

                // when
                val hasValidFormat = orderNumber.startsWith("ORD-") && orderNumber.length >= 18

                // then
                hasValidFormat shouldBe true
            }
        }

        context("data class 동작") {
            it("동일한 값을 가진 Order 객체는 같다고 판단된다 (equals)") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order1 = Order(1L, 100L, "ORD-001", items, 10000L, 0L, 10000L, null, OrderStatus.PENDING, now, now)
                val order2 = Order(1L, 100L, "ORD-001", items, 10000L, 0L, 10000L, null, OrderStatus.PENDING, now, now)

                // then
                order1 shouldBe order2
            }

            it("copy() 메서드로 일부 속성만 변경한 새 객체를 생성할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val items = listOf(OrderItem(1L, 1L, "상품", 1, 10000L, 10000L))
                val order = Order(1L, 100L, "ORD-001", items, 10000L, 0L, 10000L, null, OrderStatus.PENDING, now, now)

                // when
                val copiedOrder = order.copy(status = OrderStatus.PAID, updatedAt = LocalDateTime.now())

                // then
                copiedOrder.id shouldBe order.id
                copiedOrder.userId shouldBe order.userId
                copiedOrder.status shouldBe OrderStatus.PAID
                copiedOrder.updatedAt shouldNotBe now
                copiedOrder shouldNotBe order // status가 다르므로
            }
        }
    }

    describe("OrderItem 도메인 모델 단위 테스트") {
        context("OrderItem 객체 생성") {
            it("OrderItem 객체가 정상적으로 생성된다") {
                // given
                val orderItemId = 1L
                val productId = 1L
                val productName = "노트북"
                val quantity = 2
                val unitPrice = 1500000L
                val subtotal = unitPrice * quantity

                // when
                val orderItem = OrderItem(
                    id = orderItemId,
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    subtotal = subtotal
                )

                // then
                orderItem shouldNotBe null
                orderItem.id shouldBe orderItemId
                orderItem.productId shouldBe productId
                orderItem.productName shouldBe productName
                orderItem.quantity shouldBe quantity
                orderItem.unitPrice shouldBe unitPrice
                orderItem.subtotal shouldBe subtotal
            }

            it("수량이 1인 OrderItem을 생성할 수 있다") {
                // when
                val orderItem = OrderItem(1L, 1L, "상품", 1, 10000L, 10000L)

                // then
                orderItem.quantity shouldBe 1
                orderItem.subtotal shouldBe orderItem.unitPrice
            }

            it("수량이 여러 개인 OrderItem의 subtotal이 정확하게 계산된다") {
                // given
                val quantity = 5
                val unitPrice = 10000L
                val subtotal = unitPrice * quantity

                // when
                val orderItem = OrderItem(1L, 1L, "상품", quantity, unitPrice, subtotal)

                // then
                orderItem.subtotal shouldBe 50000L
            }
        }

        context("비즈니스 시나리오 테스트") {
            it("OrderItem의 subtotal 계산이 정확하다") {
                // given
                val quantity = 3
                val unitPrice = 15000L

                // when
                val subtotal = unitPrice * quantity
                val orderItem = OrderItem(1L, 1L, "상품", quantity, unitPrice, subtotal)

                // then
                orderItem.subtotal shouldBe 45000L
            }

            it("여러 OrderItem의 subtotal 합계가 주문 총액이 된다") {
                // given
                val items = listOf(
                    OrderItem(1L, 1L, "상품1", 2, 10000L, 20000L),
                    OrderItem(2L, 2L, "상품2", 1, 30000L, 30000L),
                    OrderItem(3L, 3L, "상품3", 3, 5000L, 15000L)
                )

                // when
                val totalAmount = items.sumOf { it.subtotal }

                // then
                totalAmount shouldBe 65000L
            }
        }

        context("data class 동작") {
            it("동일한 값을 가진 OrderItem 객체는 같다고 판단된다 (equals)") {
                // given
                val item1 = OrderItem(1L, 1L, "상품", 2, 10000L, 20000L)
                val item2 = OrderItem(1L, 1L, "상품", 2, 10000L, 20000L)

                // then
                item1 shouldBe item2
            }

            it("copy() 메서드로 일부 속성만 변경한 새 객체를 생성할 수 있다") {
                // given
                val orderItem = OrderItem(1L, 1L, "상품", 2, 10000L, 20000L)

                // when
                val copiedItem = orderItem.copy(quantity = 3, subtotal = 30000L)

                // then
                copiedItem.id shouldBe orderItem.id
                copiedItem.productId shouldBe orderItem.productId
                copiedItem.quantity shouldBe 3
                copiedItem.subtotal shouldBe 30000L
                copiedItem shouldNotBe orderItem // quantity와 subtotal이 다르므로
            }
        }
    }
})
