package com.hhplus.ecommerce.presentation.shipping

import com.hhplus.ecommerce.infrastructure.shipping.ShippingRepositoryImpl
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import com.hhplus.ecommerce.presentation.shipping.dto.ShippingDetailResponse
import com.hhplus.ecommerce.presentation.shipping.dto.UpdateShippingStatusRequest
import com.hhplus.ecommerce.presentation.shipping.dto.UpdateShippingStatusResponse
import com.hhplus.ecommerce.presentation.shipping.dto.UserShippingListResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShippingE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val shippingRepository: ShippingRepositoryImpl
) : DescribeSpec({

    lateinit var baseUrl: String

    beforeSpec {
        baseUrl = "http://localhost:$port/api"
    }

    describe("GET /shippings/{orderId}") {
        context("배송 정보 조회") {
            it("정상적으로 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val orderId = 100L

                val shipping = Shipping(
                    id = 100L,
                    orderId = orderId,
                    trackingNumber = "TRACK100",
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
                val url = "$baseUrl/shippings/${orderId}"
                val response = restTemplate.getForEntity(url, ShippingDetailResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                (body!!.shippingId) shouldBe shipping.id
                body.status shouldBe "PENDING"
            }

            it("존재하지 않는 주문 ID로 조회하면 404를 반환한다") {
                // When
                val orderId =  999999L
                val url = "$baseUrl/shippings/$orderId"
                val response = restTemplate.getForEntity(url, String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("PATCH /shippings/{shippingId}/status") {
        context("배송 상태 변경 - 정상 케이스") {
            it("PENDING에서 IN_TRANSIT으로 변경한다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(101L, 101L, ShippingStatus.PENDING, now)
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.shippingId shouldBe shipping.id
                body.orderId shouldBe shipping.orderId
                body.status shouldBe "IN_TRANSIT"
                body.deliveredAt shouldBe null

                // DB에 실제로 저장되었는지 확인
                val updatedShipping = shippingRepository.findById(shipping.id)
                updatedShipping shouldNotBe null
                updatedShipping!!.status shouldBe ShippingStatus.IN_TRANSIT
                updatedShipping.deliveredAt shouldBe null
            }

            it("IN_TRANSIT에서 DELIVERED로 변경하고 배송 완료 시간을 설정한다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(102L, 102L, ShippingStatus.IN_TRANSIT, now)
                    .copy(
                        shippingStartAt = now,
                        estimatedArrivalAt = estimatedArrivalAt
                    )
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt.plusDays(2) // 2일 지연
                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.shippingId shouldBe shipping.id
                body.orderId shouldBe shipping.orderId
                body.status shouldBe "DELIVERED"
                body.deliveredAt shouldBe deliveredAt

                // DB에 저장 확인: 지연 플래그가 true로 설정되어야 함
                val updatedShipping = shippingRepository.findById(shipping.id)
                updatedShipping shouldNotBe null
                updatedShipping!!.status shouldBe ShippingStatus.DELIVERED
                updatedShipping.deliveredAt shouldBe deliveredAt
                updatedShipping.isDelayed shouldBe true // 2일 지연
            }

            it("배송 완료 시 예상 도착일보다 빨리 도착하면 지연 플래그가 false이다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(5)
                val shipping = createShipping(104L, 104L, ShippingStatus.IN_TRANSIT, now)
                    .copy(
                        shippingStartAt = now,
                        estimatedArrivalAt = estimatedArrivalAt
                    )
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt.minusDays(1) // 1일 빨리 도착
                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.shippingId shouldBe shipping.id
                body.status shouldBe "DELIVERED"
                body.deliveredAt shouldBe deliveredAt

                // DB에서 지연 플래그 확인
                val updatedShipping = shippingRepository.findById(shipping.id)
                updatedShipping shouldNotBe null
                updatedShipping!!.status shouldBe ShippingStatus.DELIVERED
                updatedShipping.deliveredAt shouldBe deliveredAt
                updatedShipping.isDelayed shouldBe false
            }

            it("배송 완료 시 예상 도착일과 동일하면 지연 플래그가 false이다") {
                // Given
                val now = LocalDateTime.now()
                val estimatedArrivalAt = now.plusDays(3)
                val shipping = createShipping(107L, 107L, ShippingStatus.IN_TRANSIT, now)
                    .copy(
                        shippingStartAt = now,
                        estimatedArrivalAt = estimatedArrivalAt
                    )
                shippingRepository.save(shipping)

                val deliveredAt = estimatedArrivalAt // 정확히 예상일에 도착
                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = deliveredAt
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    UpdateShippingStatusResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK

                // DB에서 지연 플래그 확인 (isAfter가 false이므로 지연 아님)
                val updatedShipping = shippingRepository.findById(shipping.id)
                updatedShipping shouldNotBe null
                updatedShipping!!.isDelayed shouldBe false
            }
        }

        context("배송 상태 변경 - 예외 케이스") {
            it("잘못된 상태 전이를 시도하면 400을 반환한다 (PENDING -> DELIVERED)") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(103L, 103L, ShippingStatus.PENDING, now)
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = now
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST

                // DB 상태가 변경되지 않았는지 확인
                val unchangedShipping = shippingRepository.findById(shipping.id)
                unchangedShipping shouldNotBe null
                unchangedShipping!!.status shouldBe ShippingStatus.PENDING
            }

            it("존재하지 않는 배송 ID로 상태 변경을 시도하면 404를 반환한다") {
                // Given
                val nonExistentShippingId = 999999L
                val request = UpdateShippingStatusRequest(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                // When
                val url = "$baseUrl/shippings/$nonExistentShippingId/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("DELIVERED 상태로 변경 시 deliveredAt이 없으면 400을 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(105L, 105L, ShippingStatus.IN_TRANSIT, now)
                    .copy(shippingStartAt = now)
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "DELIVERED",
                    deliveredAt = null // deliveredAt 누락
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST

                // DB 상태가 변경되지 않았는지 확인
                val unchangedShipping = shippingRepository.findById(shipping.id)
                unchangedShipping shouldNotBe null
                unchangedShipping!!.status shouldBe ShippingStatus.IN_TRANSIT
                unchangedShipping.deliveredAt shouldBe null
            }

            it("이미 DELIVERED 상태인 배송을 다시 변경하려고 하면 410을 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(106L, 106L, ShippingStatus.DELIVERED, now)
                    .copy(
                        shippingStartAt = now.minusDays(3),
                        deliveredAt = now
                    )
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "IN_TRANSIT",
                    deliveredAt = null
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.GONE

                // DB 상태가 변경되지 않았는지 확인
                val unchangedShipping = shippingRepository.findById(shipping.id)
                unchangedShipping shouldNotBe null
                unchangedShipping!!.status shouldBe ShippingStatus.DELIVERED
            }

            it("유효하지 않은 상태 값을 전달하면 400을 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val shipping = createShipping(108L, 108L, ShippingStatus.PENDING, now)
                shippingRepository.save(shipping)

                val request = UpdateShippingStatusRequest(
                    status = "INVALID_STATUS",
                    deliveredAt = null
                )

                // When
                val url = "$baseUrl/shippings/${shipping.id}/status"
                val response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    HttpEntity(request),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST

                // DB 상태가 변경되지 않았는지 확인
                val unchangedShipping = shippingRepository.findById(shipping.id)
                unchangedShipping shouldNotBe null
                unchangedShipping!!.status shouldBe ShippingStatus.PENDING
            }
        }
    }

    describe("GET /shippings/users/{userId}/shippings") {
        context("사용자 배송 목록 조회") {
            it("기본 조회로 모든 배송 정보를 반환한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 200L

                shippingRepository.associateOrderWithUser(201L, userId)
                shippingRepository.associateOrderWithUser(202L, userId)
                shippingRepository.associateOrderWithUser(203L, userId)

                shippingRepository.save(createShipping(201L, 201L, ShippingStatus.PENDING, now))
                shippingRepository.save(createShipping(202L, 202L, ShippingStatus.IN_TRANSIT, now.minusDays(1)))
                shippingRepository.save(
                    createShipping(203L, 203L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(deliveredAt = now.minusDays(2))
                )

                // When
                val url = "$baseUrl/shippings/users/${userId}/shippings"
                val response = restTemplate.getForEntity(url, UserShippingListResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.items.size shouldBe 3
                body.page.totalElements shouldBe 3
                body.page.totalPages shouldBe 1
                body.summary.totalCount shouldBe 3
                body.summary.pendingCount shouldBe 1
                body.summary.inTransitCount shouldBe 1
                body.summary.deliveredCount shouldBe 1
            }

            it("상태 필터로 DELIVERED 배송만 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 210L

                shippingRepository.associateOrderWithUser(211L, userId)
                shippingRepository.associateOrderWithUser(212L, userId)
                shippingRepository.associateOrderWithUser(213L, userId)

                shippingRepository.save(createShipping(211L, 211L, ShippingStatus.PENDING, now))
                shippingRepository.save(
                    createShipping(212L, 212L, ShippingStatus.DELIVERED, now.minusDays(1))
                        .copy(deliveredAt = now.minusDays(1))
                )
                shippingRepository.save(
                    createShipping(213L, 213L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(deliveredAt = now.minusDays(2))
                )

                // When
                val url = "$baseUrl/shippings/users/${userId}/shippings?status=DELIVERED"
                val response = restTemplate.getForEntity(url, UserShippingListResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.items.size shouldBe 2
                body.page.totalElements shouldBe 2
                body.summary.deliveredCount shouldBe 2
                body.items.all { it.status == "DELIVERED" } shouldBe true
            }

            it("택배사 필터로 특정 택배사 배송만 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 220L

                shippingRepository.associateOrderWithUser(221L, userId)
                shippingRepository.associateOrderWithUser(222L, userId)
                shippingRepository.associateOrderWithUser(223L, userId)

                shippingRepository.save(
                    createShipping(221L, 221L, ShippingStatus.PENDING, now)
                        .copy(carrier = "CJ대한통운")
                )
                shippingRepository.save(
                    createShipping(222L, 222L, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                        .copy(carrier = "로젠택배")
                )
                shippingRepository.save(
                    createShipping(223L, 223L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(carrier = "CJ대한통운", deliveredAt = now.minusDays(2))
                )

                // When
                val url = "$baseUrl/shippings/users/${userId}/shippings?carrier=CJ대한통운"
                val response = restTemplate.getForEntity(url, UserShippingListResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.items.size shouldBe 2
                body.page.totalElements shouldBe 2
                body.items.all { it.carrier == "CJ대한통운" } shouldBe true
            }

            it("페이지네이션이 정상적으로 동작한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 230L

                // 5개의 배송 정보 생성
                for (i in 1L..5L) {
                    val id = 230L + i
                    shippingRepository.associateOrderWithUser(id, userId)
                    shippingRepository.save(
                        createShipping(id, id, ShippingStatus.PENDING, now.minusDays(i))
                    )
                }

                // When - 첫 번째 페이지
                val url1 = "$baseUrl/shippings/users/${userId}/shippings?page=0&size=2"
                val page1Response = restTemplate.getForEntity(url1, UserShippingListResponse::class.java)

                // When - 두 번째 페이지
                val url2 = "$baseUrl/shippings/users/${userId}/shippings?page=1&size=2"
                val page2Response = restTemplate.getForEntity(url2, UserShippingListResponse::class.java)

                // Then
                page1Response.statusCode shouldBe HttpStatus.OK
                page2Response.statusCode shouldBe HttpStatus.OK

                val page1 = page1Response.body!!
                val page2 = page2Response.body!!

                page1.items.size shouldBe 2
                page1.page.totalElements shouldBe 5
                page1.page.totalPages shouldBe 3
                page1.page.number shouldBe 0

                page2.items.size shouldBe 2
                page2.page.totalElements shouldBe 5
                page2.page.totalPages shouldBe 3
                page2.page.number shouldBe 1

                // 페이지 간 데이터가 다른지 확인
                val page1Ids = page1.items.map { it.shippingId }.toSet()
                val page2Ids = page2.items.map { it.shippingId }.toSet()
                page1Ids.intersect(page2Ids).isEmpty() shouldBe true
            }

            it("복합 필터 조건으로 배송을 조회한다") {
                // Given
                val now = LocalDateTime.now()
                val userId = 240L

                shippingRepository.associateOrderWithUser(241L, userId)
                shippingRepository.associateOrderWithUser(242L, userId)
                shippingRepository.associateOrderWithUser(243L, userId)
                shippingRepository.associateOrderWithUser(244L, userId)

                shippingRepository.save(
                    createShipping(241L, 241L, ShippingStatus.DELIVERED, now.minusDays(5))
                        .copy(carrier = "CJ대한통운", deliveredAt = now.minusDays(5))
                )
                shippingRepository.save(
                    createShipping(242L, 242L, ShippingStatus.DELIVERED, now.minusDays(3))
                        .copy(carrier = "로젠택배", deliveredAt = now.minusDays(3))
                )
                shippingRepository.save(
                    createShipping(243L, 243L, ShippingStatus.DELIVERED, now.minusDays(2))
                        .copy(carrier = "CJ대한통운", deliveredAt = now.minusDays(2))
                )
                shippingRepository.save(
                    createShipping(244L, 244L, ShippingStatus.IN_TRANSIT, now.minusDays(1))
                        .copy(carrier = "CJ대한통운")
                )

                // When - DELIVERED + CJ대한통운
                val carrier = "CJ대한통운"
                val from = now.minusDays(4).toString()
                val to = now.toString()
                val url = "$baseUrl/shippings/users/${userId}/shippings?status=DELIVERED&carrier=$carrier&from=$from&to=$to"
                val response = restTemplate.getForEntity(url, UserShippingListResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body
                body shouldNotBe null
                body!!.items.size shouldBe 1
                body.page.totalElements shouldBe 1
                body.items[0].shippingId shouldBe 243L
                body.items[0].status shouldBe "DELIVERED"
                body.items[0].carrier shouldBe "CJ대한통운"
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
