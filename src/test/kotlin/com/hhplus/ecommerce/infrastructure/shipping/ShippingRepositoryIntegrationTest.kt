package com.hhplus.ecommerce.infrastructure.shipping

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * ShippingRepository Integration Test
 *
 * 실제 ShippingRepositoryImpl의 동작을 검증하는 통합 테스트입니다.
 * Mock 없이 실제 인메모리 저장소 동작을 테스트합니다.
 *
 * 테스트 범위:
 * 1. CRUD 작업 - findById, findByOrderId, save
 * 2. 사용자 기반 조회 - findByUserId (정렬 확인)
 * 3. 복합 필터링 - findByUserIdWithFilters (상태, 택배사, 날짜 범위)
 * 4. ID 생성 - generateId (증가 확인)
 * 5. 송장번호 생성 - generateTrackingNumber (중복 방지)
 */
class ShippingRepositoryIntegrationTest : DescribeSpec({

    lateinit var repository: ShippingRepositoryImpl

    beforeEach {
        repository = ShippingRepositoryImpl()
    }

    describe("findById") {
        context("배송이 존재할 때") {
            it("배송 정보를 반환한다") {
                // Given
                val shipping = createShipping(1L, 100L, ShippingStatus.PENDING)
                repository.save(shipping)

                // When
                val found = repository.findById(1L)

                // Then
                found shouldNotBe null
                found!!.id shouldBe 1L
                found.orderId shouldBe 100L
                found.status shouldBe ShippingStatus.PENDING
            }
        }

        context("배송이 존재하지 않을 때") {
            it("null을 반환한다") {
                // When
                val found = repository.findById(999L)

                // Then
                found shouldBe null
            }
        }
    }

    describe("findByOrderId") {
        context("주문에 대한 배송이 존재할 때") {
            it("배송 정보를 반환한다") {
                // Given
                val shipping = createShipping(1L, 100L, ShippingStatus.PENDING)
                repository.save(shipping)

                // When
                val found = repository.findByOrderId(100L)

                // Then
                found shouldNotBe null
                found!!.id shouldBe 1L
                found.orderId shouldBe 100L
            }
        }

        context("주문에 대한 배송이 존재하지 않을 때") {
            it("null을 반환한다") {
                // When
                val found = repository.findByOrderId(999L)

                // Then
                found shouldBe null
            }
        }
    }

    describe("findByUserId") {
        context("사용자의 배송 목록을 조회할 때") {
            it("userId와 연결된 모든 배송을 createdAt 내림차순으로 반환한다") {
                // Given
                val userId = 1L
                val now = LocalDateTime.now()

                repository.associateOrderWithUser(100L, userId)
                repository.associateOrderWithUser(101L, userId)
                repository.associateOrderWithUser(102L, userId)

                repository.save(createShipping(1L, 100L, ShippingStatus.PENDING, now.minusDays(2)))
                repository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT, now.minusDays(1)))
                repository.save(createShipping(3L, 102L, ShippingStatus.DELIVERED, now))

                // When
                val result = repository.findByUserId(userId)

                // Then
                result.size shouldBe 3
                // createdAt 내림차순 확인 (최신 것이 먼저)
                result[0].id shouldBe 3L
                result[1].id shouldBe 2L
                result[2].id shouldBe 1L
            }

            it("다른 사용자의 배송은 제외한다") {
                // Given
                val userId1 = 1L
                val userId2 = 2L

                repository.associateOrderWithUser(100L, userId1)
                repository.associateOrderWithUser(101L, userId2)

                repository.save(createShipping(1L, 100L, ShippingStatus.PENDING))
                repository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT))

                // When
                val result = repository.findByUserId(userId1)

                // Then
                result.size shouldBe 1
                result[0].orderId shouldBe 100L
            }
        }
    }

    describe("findByUserIdWithFilters") {
        context("상태 필터") {
            it("특정 상태의 배송만 반환한다") {
                // Given
                val userId = 1L
                repository.associateOrderWithUser(100L, userId)
                repository.associateOrderWithUser(101L, userId)
                repository.associateOrderWithUser(102L, userId)

                repository.save(createShipping(1L, 100L, ShippingStatus.PENDING))
                repository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT))
                repository.save(createShipping(3L, 102L, ShippingStatus.DELIVERED))

                // When
                val result = repository.findByUserIdWithFilters(
                    userId = userId,
                    status = ShippingStatus.DELIVERED,
                    carrier = null,
                    from = null,
                    to = null
                )

                // Then
                result.size shouldBe 1
                result[0].status shouldBe ShippingStatus.DELIVERED
            }
        }

        context("택배사 필터") {
            it("특정 택배사의 배송만 반환한다") {
                // Given
                val userId = 1L
                repository.associateOrderWithUser(100L, userId)
                repository.associateOrderWithUser(101L, userId)

                repository.save(createShipping(1L, 100L, ShippingStatus.PENDING).copy(carrier = "CJ대한통운"))
                repository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT).copy(carrier = "로젠택배"))

                // When
                val result = repository.findByUserIdWithFilters(
                    userId = userId,
                    status = null,
                    carrier = "CJ대한통운",
                    from = null,
                    to = null
                )

                // Then
                result.size shouldBe 1
                result[0].carrier shouldBe "CJ대한통운"
            }
        }

        context("날짜 범위 필터") {
            it("특정 기간의 배송만 반환한다") {
                // Given
                val userId = 1L
                val now = LocalDateTime.now()

                repository.associateOrderWithUser(100L, userId)
                repository.associateOrderWithUser(101L, userId)
                repository.associateOrderWithUser(102L, userId)

                repository.save(createShipping(1L, 100L, ShippingStatus.PENDING, now.minusDays(10)))
                repository.save(createShipping(2L, 101L, ShippingStatus.IN_TRANSIT, now.minusDays(5)))
                repository.save(createShipping(3L, 102L, ShippingStatus.DELIVERED, now))

                // When - 최근 7일 조회
                val result = repository.findByUserIdWithFilters(
                    userId = userId,
                    status = null,
                    carrier = null,
                    from = now.minusDays(7),
                    to = now
                )

                // Then
                result.size shouldBe 2
                result.map { it.id }.toSet() shouldBe setOf(2L, 3L)
            }
        }

        context("복합 필터") {
            it("여러 조건을 동시에 적용한다") {
                // Given
                val userId = 1L
                val now = LocalDateTime.now()

                repository.associateOrderWithUser(100L, userId)
                repository.associateOrderWithUser(101L, userId)
                repository.associateOrderWithUser(102L, userId)

                repository.save(
                    createShipping(1L, 100L, ShippingStatus.DELIVERED, now.minusDays(5))
                        .copy(carrier = "CJ대한통운")
                )
                repository.save(
                    createShipping(2L, 101L, ShippingStatus.DELIVERED, now.minusDays(3))
                        .copy(carrier = "로젠택배")
                )
                repository.save(
                    createShipping(3L, 102L, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                        .copy(carrier = "CJ대한통운")
                )

                // When
                val result = repository.findByUserIdWithFilters(
                    userId = userId,
                    status = ShippingStatus.DELIVERED,
                    carrier = "CJ대한통운",
                    from = now.minusDays(7),
                    to = now
                )

                // Then
                result.size shouldBe 1
                result[0].id shouldBe 1L
                result[0].status shouldBe ShippingStatus.DELIVERED
                result[0].carrier shouldBe "CJ대한통운"
            }
        }
    }

    describe("save") {
        context("새로운 배송을 저장할 때") {
            it("배송 정보를 저장하고 반환한다") {
                // Given
                val shipping = createShipping(1L, 100L, ShippingStatus.PENDING)

                // When
                val saved = repository.save(shipping)

                // Then
                saved shouldBe shipping
                repository.findById(1L) shouldBe shipping
            }
        }

        context("기존 배송을 수정할 때") {
            it("배송 정보를 업데이트한다") {
                // Given
                val shipping = createShipping(1L, 100L, ShippingStatus.PENDING)
                repository.save(shipping)

                val updated = shipping.copy(status = ShippingStatus.IN_TRANSIT)

                // When
                repository.save(updated)

                // Then
                val found = repository.findById(1L)
                found!!.status shouldBe ShippingStatus.IN_TRANSIT
            }
        }
    }

    describe("generateId") {
        context("ID를 생성할 때") {
            it("순차적으로 증가하는 ID를 생성한다") {
                // When
                val id1 = repository.generateId()
                val id2 = repository.generateId()
                val id3 = repository.generateId()

                // Then
                id2 shouldBe id1 + 1
                id3 shouldBe id2 + 1
            }
        }
    }

    describe("generateTrackingNumber") {
        context("송장번호를 생성할 때") {
            it("12자리 숫자 문자열을 생성한다") {
                // When
                val trackingNumber = repository.generateTrackingNumber()

                // Then
                trackingNumber.length shouldBe 12
                trackingNumber.all { it.isDigit() } shouldBe true
            }

            it("중복되지 않는 송장번호를 생성한다") {
                // When
                val numbers = (1..10).map { repository.generateTrackingNumber() }.toSet()

                // Then
                numbers.size shouldBe 10
            }
        }
    }

    describe("associateOrderWithUser") {
        context("주문과 사용자를 연결할 때") {
            it("findByUserId 조회에 반영된다") {
                // Given
                val userId = 1L
                val orderId = 100L

                repository.associateOrderWithUser(orderId, userId)
                repository.save(createShipping(1L, orderId, ShippingStatus.PENDING))

                // When
                val result = repository.findByUserId(userId)

                // Then
                result.size shouldBe 1
                result[0].orderId shouldBe orderId
            }
        }
    }
}) {
    companion object {
        private fun createShipping(
            id: Long,
            orderId: Long,
            status: ShippingStatus,
            createdAt: LocalDateTime = LocalDateTime.now()
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
