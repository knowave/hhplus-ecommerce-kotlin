package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.application.shipping.dto.UpdateShippingStatusCommand
import com.hhplus.ecommerce.common.exception.AlreadyDeliveredException
import com.hhplus.ecommerce.common.exception.InvalidStatusTransitionException
import com.hhplus.ecommerce.common.exception.OrderNotFoundForShippingException
import com.hhplus.ecommerce.common.exception.ShippingNotFoundException
import com.hhplus.ecommerce.domain.shipping.repository.ShippingJpaRepository
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class ShippingServiceUnitTest : DescribeSpec({

    lateinit var shippingRepository: ShippingJpaRepository
    lateinit var shippingService: ShippingService

    beforeEach {
        shippingRepository = mockk()
        shippingService = ShippingServiceImpl(shippingRepository)
    }

    describe("getShipping") {
        context("주문 ID로 배송을 조회할 때") {
            it("정상적으로 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val shippingId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val shipping = createShipping(shippingId, orderId, ShippingStatus.PENDING, now)

                every { shippingRepository.findByOrderId(orderId) } returns shipping

                // When
                val result = shippingService.getShipping(orderId)

                // Then
                result.id shouldBe shippingId
                result.orderId shouldBe orderId
                result.status shouldBe ShippingStatus.PENDING
                result.carrier shouldBe "CJ대한통운"
                verify(exactly = 1) { shippingRepository.findByOrderId(orderId) }
            }

            it("배송 정보가 없으면 OrderNotFoundForShippingException을 발생시킨다") {
                // Given
                val orderId = UUID.randomUUID()
                every { shippingRepository.findByOrderId(orderId) } returns null

                // When & Then
                shouldThrow<OrderNotFoundForShippingException> {
                    shippingService.getShipping(orderId)
                }
                verify(exactly = 1) { shippingRepository.findByOrderId(orderId) }
            }
        }
    }

    describe("updateShippingStatus") {
        context("배송 상태를 변경할 때") {
            it("PENDING에서 IN_TRANSIT으로 변경한다") {
                // Given
                val now = LocalDateTime.now()
                val shippingId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val shipping = createShipping(shippingId, orderId, ShippingStatus.PENDING, now)
                val command = UpdateShippingStatusCommand(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                every { shippingRepository.findById(shippingId) } returns Optional.of(shipping)
                every { shippingRepository.save(any()) } answers { firstArg() }

                // When
                val result = shippingService.updateShippingStatus(shippingId, command)

                // Then
                result.shippingId shouldBe shippingId
                result.status shouldBe "IN_TRANSIT"
                result.deliveredAt shouldBe null
                verify(exactly = 1) { shippingRepository.findById(shippingId) }
                verify(exactly = 1) { shippingRepository.save(any()) }
            }

            it("IN_TRANSIT에서 DELIVERED로 변경하고 지연되지 않음을 확인한다") {
                // Given
                val now = LocalDateTime.now()
                val shippingId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(shippingId, orderId, ShippingStatus.IN_TRANSIT, now)
                // estimatedArrivalAt 설정
                shipping::class.java.getDeclaredField("estimatedArrivalAt").apply {
                    isAccessible = true
                    set(shipping, estimatedArrivalAt)
                }

                val deliveredAt = estimatedArrivalAt.minusHours(1) // 예상보다 1시간 빨리 도착
                val command = UpdateShippingStatusCommand(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                every { shippingRepository.findById(shippingId) } returns Optional.of(shipping)
                every { shippingRepository.save(any()) } answers { firstArg() }

                // When
                val result = shippingService.updateShippingStatus(shippingId, command)

                // Then
                result.status shouldBe "DELIVERED"
                result.deliveredAt shouldBe deliveredAt
                // isDelayed 검증은 저장된 객체 확인
                verify(exactly = 1) {
                    shippingRepository.save(match { it.isDelayed == false })
                }
            }

            it("IN_TRANSIT에서 DELIVERED로 변경하고 지연되었음을 확인한다") {
                // Given
                val now = LocalDateTime.now()
                val shippingId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(shippingId, orderId, ShippingStatus.IN_TRANSIT, now)
                // estimatedArrivalAt 설정
                shipping::class.java.getDeclaredField("estimatedArrivalAt").apply {
                    isAccessible = true
                    set(shipping, estimatedArrivalAt)
                }

                val deliveredAt = estimatedArrivalAt.plusDays(2) // 예상보다 2일 늦게 도착
                val command = UpdateShippingStatusCommand(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                every { shippingRepository.findById(shippingId) } returns Optional.of(shipping)
                every { shippingRepository.save(any()) } answers { firstArg() }

                // When
                val result = shippingService.updateShippingStatus(shippingId, command)

                // Then
                result.status shouldBe "DELIVERED"
                result.deliveredAt shouldBe deliveredAt
                // isDelayed 검증
                verify(exactly = 1) {
                    shippingRepository.save(match { it.isDelayed == true })
                }
            }

            it("PENDING에서 DELIVERED로 직접 변경하면 InvalidStatusTransitionException을 발생시킨다") {
                // Given
                val now = LocalDateTime.now()
                val shippingId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val shipping = createShipping(shippingId, orderId, ShippingStatus.PENDING, now)
                val command = UpdateShippingStatusCommand(
                    status = "DELIVERED",
                    deliveredAt = now
                )

                every { shippingRepository.findById(shippingId) } returns Optional.of(shipping)

                // When & Then
                shouldThrow<InvalidStatusTransitionException> {
                    shippingService.updateShippingStatus(shippingId, command)
                }
                verify(exactly = 1) { shippingRepository.findById(shippingId) }
                verify(exactly = 0) { shippingRepository.save(any()) }
            }

            it("이미 DELIVERED 상태인 배송을 변경하려 하면 AlreadyDeliveredException을 발생시킨다") {
                // Given
                val now = LocalDateTime.now()
                val shippingId = UUID.randomUUID()
                val orderId = UUID.randomUUID()
                val shipping = createShipping(shippingId, orderId, ShippingStatus.DELIVERED, now)
                shipping.deliveredAt = now

                val command = UpdateShippingStatusCommand(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                every { shippingRepository.findById(shippingId) } returns Optional.of(shipping)

                // When & Then
                shouldThrow<AlreadyDeliveredException> {
                    shippingService.updateShippingStatus(shippingId, command)
                }
                verify(exactly = 1) { shippingRepository.findById(shippingId) }
                verify(exactly = 0) { shippingRepository.save(any()) }
            }

            it("존재하지 않는 배송 ID로 변경하려 하면 ShippingNotFoundException을 발생시킨다") {
                // Given
                val shippingId = UUID.randomUUID()
                val command = UpdateShippingStatusCommand(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                every { shippingRepository.findById(shippingId) } returns Optional.empty()

                // When & Then
                shouldThrow<ShippingNotFoundException> {
                    shippingService.updateShippingStatus(shippingId, command)
                }
                verify(exactly = 1) { shippingRepository.findById(shippingId) }
            }
        }
    }

    describe("getUserShippings") {
        context("사용자의 배송 목록을 조회할 때") {
            it("필터 없이 모든 배송을 조회한다") {
                // Given
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()
                val shippings = listOf(
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.PENDING, now),
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.IN_TRANSIT, now.minusDays(1)),
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.DELIVERED, now.minusDays(2)).also {
                        it.deliveredAt = now.minusDays(2)
                    }
                )

                val pageable = PageRequest.of(0, 10)
                val page = PageImpl(shippings, pageable, shippings.size.toLong())

                every {
                    shippingRepository.findByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )
                } returns page

                every {
                    shippingRepository.findAllByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null
                    )
                } returns shippings

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
                result.page.totalElements shouldBe 3
                result.page.totalPages shouldBe 1
                result.summary.totalCount shouldBe 3
                result.summary.pendingCount shouldBe 1
                result.summary.inTransitCount shouldBe 1
                result.summary.deliveredCount shouldBe 1
            }

            it("상태 필터를 적용하여 조회한다") {
                // Given
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()
                val deliveredShippings = listOf(
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.DELIVERED, now.minusDays(1)).also {
                        it.deliveredAt = now.minusDays(1)
                    },
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.DELIVERED, now.minusDays(2)).also {
                        it.deliveredAt = now.minusDays(2)
                    }
                )

                val pageable = PageRequest.of(0, 10)
                val page = PageImpl(deliveredShippings, pageable, deliveredShippings.size.toLong())

                every {
                    shippingRepository.findByUserIdWithFilters(
                        userId = userId,
                        status = ShippingStatus.DELIVERED,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )
                } returns page

                every {
                    shippingRepository.findAllByUserIdWithFilters(
                        userId = userId,
                        status = ShippingStatus.DELIVERED,
                        carrier = null,
                        from = null,
                        to = null
                    )
                } returns deliveredShippings

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
                result.page.totalElements shouldBe 2
                result.summary.deliveredCount shouldBe 2
                result.summary.pendingCount shouldBe 0
            }

            it("페이지네이션을 적용하여 조회한다") {
                // Given
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()
                val allShippings = (1..5).map {
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.PENDING, now.minusDays(it.toLong()))
                }

                val pageable1 = PageRequest.of(0, 2)
                val page1Content = allShippings.take(2)
                val page1 = PageImpl(page1Content, pageable1, allShippings.size.toLong())

                val pageable2 = PageRequest.of(1, 2)
                val page2Content = allShippings.drop(2).take(2)
                val page2 = PageImpl(page2Content, pageable2, allShippings.size.toLong())

                every {
                    shippingRepository.findByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable1
                    )
                } returns page1

                every {
                    shippingRepository.findByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable2
                    )
                } returns page2

                every {
                    shippingRepository.findAllByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null
                    )
                } returns allShippings

                // When - 첫 번째 페이지 (2개씩)
                val result1 = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = null,
                    to = null,
                    page = 0,
                    size = 2
                )

                // When - 두 번째 페이지 (2개씩)
                val result2 = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = null,
                    to = null,
                    page = 1,
                    size = 2
                )

                // Then
                result1.items.size shouldBe 2
                result1.page.totalElements shouldBe 5
                result1.page.totalPages shouldBe 3
                result1.page.number shouldBe 0

                result2.items.size shouldBe 2
                result2.page.totalElements shouldBe 5
                result2.page.totalPages shouldBe 3
                result2.page.number shouldBe 1

                // 다른 데이터 확인
                result1.items[0].id shouldBe page1Content[0].id
                result1.items[1].id shouldBe page1Content[1].id
                result2.items[0].id shouldBe page2Content[0].id
                result2.items[1].id shouldBe page2Content[1].id
            }

            it("빈 페이지를 조회하면 빈 결과를 반환한다") {
                // Given
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()
                val allShippings = listOf(
                    createShipping(UUID.randomUUID(), UUID.randomUUID(), ShippingStatus.PENDING, now)
                )

                val pageable = PageRequest.of(10, 10)
                val emptyPage = PageImpl(emptyList<Shipping>(), pageable, allShippings.size.toLong())

                every {
                    shippingRepository.findByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null,
                        pageable = pageable
                    )
                } returns emptyPage

                every {
                    shippingRepository.findAllByUserIdWithFilters(
                        userId = userId,
                        status = null,
                        carrier = null,
                        from = null,
                        to = null
                    )
                } returns allShippings

                // When - 존재하지 않는 페이지 조회
                val result = shippingService.getUserShippings(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = null,
                    to = null,
                    page = 10,
                    size = 10
                )

                // Then
                result.items.size shouldBe 0
                result.page.totalElements shouldBe 1
                result.page.totalPages shouldBe 1
                result.page.number shouldBe 10
            }
        }
    }
}) {
    companion object {
        private fun createShipping(
            id: UUID,
            orderId: UUID,
            status: ShippingStatus,
            createdAt: LocalDateTime
        ): Shipping {
            val shipping = Shipping(
                orderId = orderId,
                carrier = "CJ대한통운",
                trackingNumber = "TRACK${id.toString().take(8)}",
                shippingStartAt = if (status != ShippingStatus.PENDING) createdAt else null,
                estimatedArrivalAt = createdAt.plusDays(3),
                deliveredAt = if (status == ShippingStatus.DELIVERED) createdAt.plusDays(3) else null,
                status = status,
                isDelayed = false,
                isExpired = false
            )
            // BaseEntity의 필드를 리플렉션으로 설정 (테스트용)
            shipping::class.java.superclass.getDeclaredField("id").apply {
                isAccessible = true
                set(shipping, id)
            }
            shipping::class.java.superclass.getDeclaredField("createdAt").apply {
                isAccessible = true
                set(shipping, createdAt)
            }
            shipping::class.java.superclass.getDeclaredField("updatedAt").apply {
                isAccessible = true
                set(shipping, createdAt)
            }
            return shipping
        }
    }
}
