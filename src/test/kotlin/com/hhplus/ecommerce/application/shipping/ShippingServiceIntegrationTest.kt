package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.application.shipping.dto.UpdateShippingStatusCommand
import com.hhplus.ecommerce.common.exception.InvalidStatusTransitionException
import com.hhplus.ecommerce.infrastructure.shipping.ShippingRepositoryImpl
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.LocalDateTime

class ShippingServiceIntegrationTest : DescribeSpec({

    lateinit var shippingRepository: ShippingRepositoryImpl
    lateinit var shippingService: ShippingService

    beforeEach {
        shippingRepository = ShippingRepositoryImpl()
        shippingService = ShippingServiceImpl(shippingRepository)
    }

    describe("getShipping") {
        context("저장된 배송 정보를 조회할 때") {
            it("정상적으로 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val orderId = 100L

                val shipping = Shipping(
                    id = 1L,
                    orderId = orderId,
                    trackingNumber = "TRACK001",
                    carrier = "CJ대한통운",
                    shippingStartAt = null,
                    estimatedArrivalAt = now.plusDays(3),
                    deliveredAt = null,
                    status = ShippingStatus.PENDING,
                    isDelayed = false,
                    isExpired = false,
                    createdAt = now,
                    updatedAt = now
                )
                shippingRepository.save(shipping)

                // When
                val result = shippingService.getShipping(orderId)

                // Then
                result.id shouldBe 1L
                result.orderId shouldBe orderId
                result.trackingNumber shouldBe "TRACK001"
                result.carrier shouldBe "CJ대한통운"
                result.status shouldBe "PENDING"
            }

            it("존재하지 않는 주문 ID로 조회하면 OrderNotFoundForShippingException을 발생시킨다") {
                val orderId = 999L
                // When & Then
                assertThatThrownBy {
                    shippingService.getShipping(orderId)
                }.hasMessageContaining("Order not found with id: $orderId")
            }
        }
    }

    describe("updateShippingStatus") {
        context("배송 상태를 변경할 때") {
            it("PENDING에서 IN_TRANSIT으로 변경한다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(1L, 100L, ShippingStatus.PENDING, now)
                shippingRepository.save(shipping)

                val command = UpdateShippingStatusCommand(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                // When
                val result = shippingService.updateShippingStatus(1L, command)

                // Then
                result.status shouldBe "IN_TRANSIT"
                result.deliveredAt shouldBe null

                // Repository에서 확인
                val updated = shippingRepository.findById(1L)
                updated!!.status shouldBe ShippingStatus.IN_TRANSIT
            }

            it("IN_TRANSIT에서 DELIVERED로 변경하고 지연 여부를 계산한다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(1L, 100L, ShippingStatus.IN_TRANSIT, now)
                    .copy(
                        shippingStartAt = now,
                        estimatedArrivalAt = estimatedArrivalAt
                    )
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt.plusDays(2) // 예상보다 2일 늦게 도착
                val command = UpdateShippingStatusCommand(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val result = shippingService.updateShippingStatus(1L, command)

                // Then
                result.status shouldBe "DELIVERED"
                result.deliveredAt shouldBe deliveredAt

                // Repository에서 확인
                val updated = shippingRepository.findById(1L)
                updated!!.status shouldBe ShippingStatus.DELIVERED
                updated.deliveredAt shouldBe deliveredAt
                updated.isDelayed shouldBe true // 지연됨
            }

            it("IN_TRANSIT에서 DELIVERED로 변경하고 정시 배송을 확인한다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(1L, 100L, ShippingStatus.IN_TRANSIT, now)
                    .copy(
                        shippingStartAt = now,
                        estimatedArrivalAt = estimatedArrivalAt
                    )
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt.minusHours(1) // 예상보다 1시간 일찍 도착
                val command = UpdateShippingStatusCommand(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val result = shippingService.updateShippingStatus(1L, command)

                // Then
                result.status shouldBe "DELIVERED"
                result.deliveredAt shouldBe deliveredAt

                // Repository에서 확인
                val updated = shippingRepository.findById(1L)
                updated!!.isDelayed shouldBe false // 정시 배송
            }

            it("PENDING에서 DELIVERED로 직접 변경하면 InvalidStatusTransitionException을 발생시킨다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(1L, 100L, ShippingStatus.PENDING, now)
                shippingRepository.save(shipping)

                val command = UpdateShippingStatusCommand(
                    status = "DELIVERED",
                    deliveredAt = now
                )

                // When & Then
                assertThatThrownBy {
                    shippingService.updateShippingStatus(1L, command)
                }.isInstanceOf(InvalidStatusTransitionException::class.java)
                    .hasMessageContaining("Cannot transition from PENDING to DELIVERED")
            }
        }
    }

    describe("getUserShippings") {
        context("사용자의 배송 목록을 조회할 때") {
            it("기본 조회로 모든 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 1L

                shippingRepository.associateOrderWithUser(100L, userId)
                shippingRepository.associateOrderWithUser(101L, userId)
                shippingRepository.associateOrderWithUser(102L, userId)

                shippingRepository.save(createShipping(1L, 100L, ShippingStatus.PENDING, now))
                shippingRepository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT, now.minusDays(1)))
                shippingRepository.save(
                    createShipping(3L, 102L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(deliveredAt = now.minusDays(2))
                )

                // When
                val result = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = null,
                    to = null,
                    page = 0,
                    size = 10
                )

                // Then
                result.items.size shouldBe 3
                result.page.totalPages shouldBe 1
                result.summary.totalCount shouldBe 3
                result.summary.pendingCount shouldBe 1
                result.summary.inTransitCount shouldBe 1
                result.summary.deliveredCount shouldBe 1
            }

            it("상태 필터로 DELIVERED 배송만 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 1L

                shippingRepository.associateOrderWithUser(100L, userId)
                shippingRepository.associateOrderWithUser(101L, userId)
                shippingRepository.associateOrderWithUser(102L, userId)

                shippingRepository.save(createShipping(1L, 100L, ShippingStatus.PENDING, now))
                shippingRepository.save(
                    createShipping(2L, 101L, ShippingStatus.DELIVERED, now.minusDays(1))
                        .copy(deliveredAt = now.minusDays(1))
                )
                shippingRepository.save(
                    createShipping(3L, 102L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(deliveredAt = now.minusDays(2))
                )

                // When
                val result = shippingService.getUserShippings(
                    userId = userId,
                    status = "DELIVERED",
                    carrier = null,
                    from = null,
                    to = null,
                    page = 0,
                    size = 10
                )

                // Then
                result.items.size shouldBe 2
                result.summary.deliveredCount shouldBe 2
                result.summary.pendingCount shouldBe 0
            }

            it("택배사 필터로 특정 택배사 배송만 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 1L

                shippingRepository.associateOrderWithUser(100L, userId)
                shippingRepository.associateOrderWithUser(101L, userId)
                shippingRepository.associateOrderWithUser(102L, userId)

                shippingRepository.save(
                    createShipping(1L, 100L, ShippingStatus.PENDING, now)
                        .copy(carrier = "CJ대한통운")
                )
                shippingRepository.save(
                    createShipping(2L, 101L, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                        .copy(carrier = "로젠택배")
                )
                shippingRepository.save(
                    createShipping(3L, 102L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(carrier = "CJ대한통운", deliveredAt = now.minusDays(2))
                )

                // When
                val result = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = "CJ대한통운",
                    from = null,
                    to = null,
                    page = 0,
                    size = 10
                )

                // Then
                result.items.size shouldBe 2
                result.items.all { it.carrier == "CJ대한통운" } shouldBe true
            }

            it("날짜 범위 필터로 특정 기간의 배송만 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 1L

                shippingRepository.associateOrderWithUser(100L, userId)
                shippingRepository.associateOrderWithUser(101L, userId)
                shippingRepository.associateOrderWithUser(102L, userId)

                shippingRepository.save(createShipping(1L, 100L, ShippingStatus.PENDING, now.minusDays(10)))
                shippingRepository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT, now.minusDays(5)))
                shippingRepository.save(createShipping(3L, 102L, ShippingStatus.DELIVERED, now.minusDays(1)))

                // When
                val result = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = now.minusDays(7).toString(),
                    to = now.toString(),
                    page = 0,
                    size = 10
                )

                // Then
                result.items.size shouldBe 2
            }

            it("페이지네이션이 정상적으로 동작한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 1L

                // 5개의 배송 정보 생성
                for (i in 1L..5L) {
                    shippingRepository.associateOrderWithUser(100L + i, userId)
                    shippingRepository.save(
                        createShipping(i, 100L + i, ShippingStatus.PENDING, now.minusDays(i))
                    )
                }

                // When - 첫 번째 페이지 (2개씩)
                val page1 = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = null,
                    to = null,
                    page = 0,
                    size = 2
                )

                // When - 두 번째 페이지 (2개씩)
                val page2 = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = null,
                    to = null,
                    page = 1,
                    size = 2
                )

                // Then
                page1.items.size shouldBe 2
                page1.page.totalPages shouldBe 3
                page1.page.number shouldBe 0

                page2.page.size shouldBe 2
                page2.items.size shouldBe 2
                page2.page.totalPages shouldBe 3
                page2.page.number shouldBe 1

                // 페이지 간 데이터가 다른지 확인
                val page1Ids = page1.items.map { it.id }.toSet()
                val page2Ids = page2.items.map { it.id }.toSet()
                page1Ids.intersect(page2Ids).isEmpty() shouldBe true
            }

            it("복합 필터 조건으로 배송을 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 1L

                shippingRepository.associateOrderWithUser(100L, userId)
                shippingRepository.associateOrderWithUser(101L, userId)
                shippingRepository.associateOrderWithUser(102L, userId)
                shippingRepository.associateOrderWithUser(103L, userId)

                shippingRepository.save(
                    createShipping(1L, 100L, ShippingStatus.DELIVERED, now.minusDays(5))
                        .copy(carrier = "CJ대한통운", deliveredAt = now.minusDays(5))
                )
                shippingRepository.save(
                    createShipping(2L, 101L, ShippingStatus.DELIVERED, now.minusDays(3))
                        .copy(carrier = "로젠택배", deliveredAt = now.minusDays(3))
                )
                shippingRepository.save(
                    createShipping(3L, 102L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(carrier = "CJ대한통운", deliveredAt = now.minusDays(2))
                )
                shippingRepository.save(
                    createShipping(4L, 103L, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                        .copy(carrier = "CJ대한통운")
                )

                // When - DELIVERED + CJ대한통운 + 최근 4일
                val result = shippingService.getUserShippings(
                    userId = userId,
                    status = "DELIVERED",
                    carrier = "CJ대한통운",
                    from = now.minusDays(4).toString(),
                    to = now.toString(),
                    page = 0,
                    size = 10
                )

                // Then
                result.items.size shouldBe 1
                result.items[0].id shouldBe 3L
                result.items[0].status shouldBe "DELIVERED"
                result.items[0].carrier shouldBe "CJ대한통운"
            }
        }
    }
}) {
    companion object {
        private fun createShipping(
            id: Long,
            orderId: Long,
            status: ShippingStatus,
            createdAt: LocalDateTime
        ): Shipping {
            return Shipping(
                id = id,
                orderId = orderId,
                carrier = "CJ대한통운",
                trackingNumber = "TRACK${String.format("%03d", id)}",
                shippingStartAt = if (status != ShippingStatus.PENDING) createdAt else null,
                estimatedArrivalAt = createdAt.plusDays(3),
                deliveredAt = if (status == ShippingStatus.DELIVERED) createdAt.plusDays(3) else null,
                status = status,
                isDelayed = false,
                isExpired = false,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }
}
