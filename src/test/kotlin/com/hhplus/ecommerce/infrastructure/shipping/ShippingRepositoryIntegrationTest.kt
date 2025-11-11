package com.hhplus.ecommerce.infrastructure.shipping

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.util.UUID

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
 * 4. 송장번호 생성 - generateTrackingNumber (중복 방지)
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
                val orderId = UUID.randomUUID()
                val shipping = createShipping(orderId, ShippingStatus.PENDING)
                repository.save(shipping)

                // When
                val found = repository.findById(shipping.id!!)

                // Then
                found shouldNotBe null
                found!!.id shouldBe shipping.id
                found.orderId shouldBe orderId
                found.status shouldBe ShippingStatus.PENDING
            }
        }

        context("배송이 존재하지 않을 때") {
            it("null을 반환한다") {
                // When
                val found = repository.findById(UUID.randomUUID())

                // Then
                found shouldBe null
            }
        }
    }

    describe("findByOrderId") {
        context("주문에 대한 배송이 존재할 때") {
            it("배송 정보를 반환한다") {
                // Given
                val orderId = UUID.randomUUID()
                val shipping = createShipping(orderId, ShippingStatus.PENDING)
                repository.save(shipping)

                // When
                val found = repository.findByOrderId(orderId)

                // Then
                found shouldNotBe null
                found!!.id shouldBe shipping.id
                found.orderId shouldBe orderId
            }
        }

        context("주문에 대한 배송이 존재하지 않을 때") {
            it("null을 반환한다") {
                // When
                val found = repository.findByOrderId(UUID.randomUUID())

                // Then
                found shouldBe null
            }
        }
    }

    describe("findByUserId") {
        context("사용자의 배송 목록을 조회할 때") {
            it("userId와 연결된 모든 배송을 createdAt 내림차순으로 반환한다") {
                // Given
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()

                val orderId1 = UUID.randomUUID()
                val orderId2 = UUID.randomUUID()
                val orderId3 = UUID.randomUUID()

                repository.associateOrderWithUser(orderId1, userId)
                repository.associateOrderWithUser(orderId2, userId)
                repository.associateOrderWithUser(orderId3, userId)

                val shipping1 = createShipping(orderId1, ShippingStatus.PENDING, now.minusDays(2))
                val shipping2 = createShipping(orderId2, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                val shipping3 = createShipping(orderId3, ShippingStatus.DELIVERED, now)

                repository.save(shipping1)
                repository.save(shipping2)
                repository.save(shipping3)

                // When
                val result = repository.findByUserId(userId)

                // Then
                result.size shouldBe 3
                // createdAt 내림차순 확인 (최신 것이 먼저)
                result[0].orderId shouldBe orderId3
                result[1].orderId shouldBe orderId2
                result[2].orderId shouldBe orderId1
            }

            it("다른 사용자의 배송은 제외한다") {
                // Given
                val userId1 = UUID.randomUUID()
                val userId2 = UUID.randomUUID()

                val orderId1 = UUID.randomUUID()
                val orderId2 = UUID.randomUUID()

                repository.associateOrderWithUser(orderId1, userId1)
                repository.associateOrderWithUser(orderId2, userId2)

                repository.save(createShipping(orderId1, ShippingStatus.PENDING))
                repository.save(createShipping(orderId2, ShippingStatus.IN_TRANSIT))

                // When
                val result = repository.findByUserId(userId1)

                // Then
                result.size shouldBe 1
                result[0].orderId shouldBe orderId1
            }
        }
    }

    describe("findByUserIdWithFilters") {
        context("상태 필터") {
            it("특정 상태의 배송만 반환한다") {
                // Given
                val userId = UUID.randomUUID()
                val orderId1 = UUID.randomUUID()
                val orderId2 = UUID.randomUUID()
                val orderId3 = UUID.randomUUID()

                repository.associateOrderWithUser(orderId1, userId)
                repository.associateOrderWithUser(orderId2, userId)
                repository.associateOrderWithUser(orderId3, userId)

                repository.save(createShipping(orderId1, ShippingStatus.PENDING))
                repository.save(createShipping(orderId2, ShippingStatus.IN_TRANSIT))
                repository.save(createShipping(orderId3, ShippingStatus.DELIVERED))

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
                val userId = UUID.randomUUID()
                val orderId1 = UUID.randomUUID()
                val orderId2 = UUID.randomUUID()

                repository.associateOrderWithUser(orderId1, userId)
                repository.associateOrderWithUser(orderId2, userId)

                val shipping1 = createShipping(orderId1, ShippingStatus.PENDING)
                shipping1::class.java.getDeclaredField("carrier").apply {
                    isAccessible = true
                    set(shipping1, "CJ대한통운")
                }
                repository.save(shipping1)

                val shipping2 = createShipping(orderId2, ShippingStatus.IN_TRANSIT)
                shipping2::class.java.getDeclaredField("carrier").apply {
                    isAccessible = true
                    set(shipping2, "로젠택배")
                }
                repository.save(shipping2)

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
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()

                val orderId1 = UUID.randomUUID()
                val orderId2 = UUID.randomUUID()
                val orderId3 = UUID.randomUUID()

                repository.associateOrderWithUser(orderId1, userId)
                repository.associateOrderWithUser(orderId2, userId)
                repository.associateOrderWithUser(orderId3, userId)

                val shipping1 = createShipping(orderId1, ShippingStatus.PENDING, now.minusDays(10))
                val shipping2 = createShipping(orderId2, ShippingStatus.IN_TRANSIT, now.minusDays(5))
                val shipping3 = createShipping(orderId3, ShippingStatus.DELIVERED, now)

                repository.save(shipping1)
                repository.save(shipping2)
                repository.save(shipping3)

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
                result.map { it.orderId }.toSet() shouldBe setOf(orderId2, orderId3)
            }
        }

        context("복합 필터") {
            it("여러 조건을 동시에 적용한다") {
                // Given
                val userId = UUID.randomUUID()
                val now = LocalDateTime.now()

                val orderId1 = UUID.randomUUID()
                val orderId2 = UUID.randomUUID()
                val orderId3 = UUID.randomUUID()

                repository.associateOrderWithUser(orderId1, userId)
                repository.associateOrderWithUser(orderId2, userId)
                repository.associateOrderWithUser(orderId3, userId)

                val shipping1 = createShipping(orderId1, ShippingStatus.DELIVERED, now.minusDays(5))
                shipping1::class.java.getDeclaredField("carrier").apply {
                    isAccessible = true
                    set(shipping1, "CJ대한통운")
                }
                repository.save(shipping1)

                val shipping2 = createShipping(orderId2, ShippingStatus.DELIVERED, now.minusDays(3))
                shipping2::class.java.getDeclaredField("carrier").apply {
                    isAccessible = true
                    set(shipping2, "로젠택배")
                }
                repository.save(shipping2)

                val shipping3 = createShipping(orderId3, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                shipping3::class.java.getDeclaredField("carrier").apply {
                    isAccessible = true
                    set(shipping3, "CJ대한통운")
                }
                repository.save(shipping3)

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
                result[0].orderId shouldBe orderId1
                result[0].status shouldBe ShippingStatus.DELIVERED
                result[0].carrier shouldBe "CJ대한통운"
            }
        }
    }

    describe("save") {
        context("새로운 배송을 저장할 때") {
            it("배송 정보를 저장하고 반환한다") {
                // Given
                val orderId = UUID.randomUUID()
                val shipping = createShipping(orderId, ShippingStatus.PENDING)

                // When
                val saved = repository.save(shipping)

                // Then
                saved.id shouldNotBe null
                repository.findById(saved.id!!) shouldNotBe null
            }
        }

        context("기존 배송을 수정할 때") {
            it("배송 정보를 업데이트한다") {
                // Given
                val orderId = UUID.randomUUID()
                val shipping = createShipping(orderId, ShippingStatus.PENDING)
                repository.save(shipping)

                shipping.status = ShippingStatus.IN_TRANSIT

                // When
                repository.save(shipping)

                // Then
                val found = repository.findById(shipping.id!!)
                found!!.status shouldBe ShippingStatus.IN_TRANSIT
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
                val userId = UUID.randomUUID()
                val orderId = UUID.randomUUID()

                repository.associateOrderWithUser(orderId, userId)
                repository.save(createShipping(orderId, ShippingStatus.PENDING))

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
            orderId: UUID,
            status: ShippingStatus,
            createdAt: LocalDateTime = LocalDateTime.now()
        ): Shipping {
            val shipping = Shipping(
                orderId = orderId,
                carrier = "CJ대한통운",
                trackingNumber = "TRACK${orderId.toString().take(8)}",
                shippingStartAt = if (status != ShippingStatus.PENDING) createdAt else null,
                estimatedArrivalAt = createdAt.plusDays(3),
                deliveredAt = if (status == ShippingStatus.DELIVERED) createdAt.plusDays(3) else null,
                status = status,
                isDelayed = false,
                isExpired = false
            )
            // createdAt과 updatedAt 설정
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
