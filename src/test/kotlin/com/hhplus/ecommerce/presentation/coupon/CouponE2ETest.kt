package com.hhplus.ecommerce.presentation.coupon

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import com.hhplus.ecommerce.presentation.coupon.dto.*
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val userService: UserService,
    private val couponService: CouponService,
    private val userCouponRepository: UserCouponJpaRepository
) : DescribeSpec({

    // 테스트용 데이터 ID
    var user1Id: UUID? = null
    var user2Id: UUID? = null
    var availableCouponId: UUID? = null

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    beforeSpec {
        // 테스트용 사용자 2명 생성
        val createUser1Command = CreateUserCommand(balance = 500000L)
        val savedUser1 = userService.createUser(createUser1Command)
        user1Id = savedUser1.id!!

        val createUser2Command = CreateUserCommand(balance = 500000L)
        val savedUser2 = userService.createUser(createUser2Command)
        user2Id = savedUser2.id!!

        // 사용 가능한 쿠폰 ID 가져오기
        val availableCoupons = couponService.getAvailableCoupons()
        availableCouponId = availableCoupons.coupons.firstOrNull()?.id
            ?: throw IllegalStateException("No available coupons")
    }

    afterSpec {
        // 테스트 종료 후 사용자 쿠폰 데이터 정리
        userCouponRepository.deleteAll()
    }

    describe("Coupon API E2E Tests") {

        describe("사용 가능한 쿠폰 목록 조회") {
            it("발급 가능한 쿠폰 목록을 조회할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(url("/coupons/available"), AvailableCouponResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.coupons.shouldNotBeEmpty()
            }

            it("쿠폰 목록에는 쿠폰 정보가 포함되어야 한다") {
                // When
                val response = restTemplate.getForEntity(url("/coupons/available"), AvailableCouponResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.coupons?.first()?.let { coupon ->
                    coupon.id shouldNotBe null
                    coupon.couponName shouldNotBe null
                    coupon.description shouldNotBe null
                    (coupon.discountRate > 0) shouldBe true
                    (coupon.totalQuantity > 0) shouldBe true
                    (coupon.remainingQuantity >= 0) shouldBe true
                    coupon.issuePeriod shouldNotBe null
                    (coupon.validityDays > 0) shouldBe true
                }
            }
        }

        describe("쿠폰 상세 조회") {
            it("쿠폰 ID로 쿠폰 상세 정보를 조회할 수 있어야 한다") {
                // Given
                val couponId = availableCouponId!!

                // When
                val response = restTemplate.getForEntity(url("/coupons/$couponId"), CouponDetailResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { coupon ->
                    coupon.id shouldBe couponId
                    coupon.couponName shouldNotBe null
                    coupon.description shouldNotBe null
                    (coupon.discountRate > 0) shouldBe true
                    (coupon.totalQuantity > 0) shouldBe true
                    (coupon.issuedQuantity >= 0) shouldBe true
                    (coupon.remainingQuantity >= 0) shouldBe true
                    coupon.issuePeriod shouldNotBe null
                    (coupon.validityDays > 0) shouldBe true
                    coupon.createdAt shouldNotBe null
                }
            }

            it("존재하지 않는 쿠폰 조회 시 404를 반환해야 한다") {
                // Given
                val invalidCouponId = UUID.randomUUID()

                // When
                val response = restTemplate.getForEntity(url("/coupons/$invalidCouponId"), String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("쿠폰 상세 정보에는 발급 가능 여부가 포함되어야 한다") {
                // Given
                val couponId = availableCouponId!!

                // When
                val response = restTemplate.getForEntity(url("/coupons/$couponId"), CouponDetailResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.isAvailable shouldNotBe null
            }
        }

        describe("쿠폰 발급") {
            it("사용자에게 쿠폰을 발급할 수 있어야 한다") {
                // Given
                val userId = user1Id!!
                val couponId = availableCouponId!!

                val request = IssueCouponRequest(userId = userId)

                // When
                val response = restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request,
                    IssueCouponResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { issued ->
                    issued.userCouponId shouldNotBe null
                    issued.userId shouldBe userId
                    issued.couponId shouldBe couponId
                    issued.couponName shouldNotBe null
                    (issued.discountRate > 0) shouldBe true
                    issued.status shouldBe "AVAILABLE"
                    issued.issuedAt shouldNotBe null
                    issued.expiresAt shouldNotBe null
                    (issued.remainingQuantity >= 0) shouldBe true
                }
            }

            it("쿠폰 발급 후 잔여 수량이 감소해야 한다") {
                // Given
                val userId = user2Id!!
                val couponId = availableCouponId!!

                // 발급 전 잔여 수량 확인
                val beforeResponse = restTemplate.getForEntity(url("/coupons/$couponId"), CouponDetailResponse::class.java)
                val remainingBefore = beforeResponse.body?.remainingQuantity ?: 0

                // When - 쿠폰 발급
                val request = IssueCouponRequest(userId = userId)
                val issueResponse = restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request,
                    IssueCouponResponse::class.java
                )

                // Then
                issueResponse.statusCode shouldBe HttpStatus.OK
                issueResponse.body?.remainingQuantity shouldBe (remainingBefore - 1)
            }

            it("존재하지 않는 쿠폰 발급 시 에러가 발생해야 한다") {
                // Given
                val userId = user1Id!!
                val invalidCouponId = UUID.randomUUID()
                val request = IssueCouponRequest(userId = userId)

                // When
                val response = restTemplate.postForEntity(
                    url("/coupons/$invalidCouponId/issue"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("같은 쿠폰을 중복 발급할 수 없어야 한다") {
                // Given - 새로운 사용자 생성 (이미 발급받지 않은 사용자)
                val newUser = userService.createUser(CreateUserCommand(balance = 500000L))
                val userId = newUser.id!!
                val couponId = availableCouponId!!
                val request = IssueCouponRequest(userId = userId)

                // 첫 번째 발급
                restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request,
                    IssueCouponResponse::class.java
                )

                // When - 두 번째 발급 시도
                val response = restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request,
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        describe("사용자 쿠폰 목록 조회") {
            it("사용자가 보유한 쿠폰 목록을 조회할 수 있어야 한다") {
                // Given - user1은 이미 쿠폰을 발급받음
                val userId = user1Id!!

                // When
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId"),
                    UserCouponListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.userId shouldBe userId
                    result.coupons.shouldNotBeEmpty()
                    (result.coupons.size >= 1) shouldBe true
                    result.summary shouldNotBe null
                }
            }

            it("사용자 쿠폰 목록에 요약 정보가 포함되어야 한다") {
                // Given - user1은 이미 쿠폰을 발급받음
                val userId = user1Id!!

                // When
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId"),
                    UserCouponListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.summary?.let { summary ->
                    (summary.totalCount >= 0) shouldBe true
                    (summary.availableCount >= 0) shouldBe true
                    (summary.usedCount >= 0) shouldBe true
                    (summary.expiredCount >= 0) shouldBe true
                }
            }

            it("쿠폰이 없는 사용자는 빈 목록을 반환해야 한다") {
                // Given - 새로운 사용자 생성 (쿠폰 없음)
                val newUser = userService.createUser(CreateUserCommand(balance = 500000L))
                val userId = newUser.id!!

                // When
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId"),
                    UserCouponListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.coupons?.size shouldBe 0
                response.body?.summary?.totalCount shouldBe 0
            }

            it("상태별로 쿠폰을 필터링하여 조회할 수 있어야 한다") {
                // Given - user1은 이미 쿠폰을 발급받음
                val userId = user1Id!!

                // When - AVAILABLE 상태 쿠폰만 조회
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId?status=AVAILABLE"),
                    UserCouponListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.coupons?.forEach { coupon ->
                    coupon.status shouldBe "AVAILABLE"
                }
            }
        }

        describe("사용자의 특정 쿠폰 조회") {
            it("사용자 ID와 사용자 쿠폰 ID로 특정 쿠폰을 조회할 수 있어야 한다") {
                // Given - user1이 이미 발급받은 쿠폰 조회
                val userId = user1Id!!

                // 먼저 사용자의 쿠폰 목록을 조회하여 userCouponId 얻기
                val listResponse = restTemplate.getForEntity(
                    url("/coupons/users/$userId"),
                    UserCouponListResponse::class.java
                )
                val userCouponId = listResponse.body?.coupons?.firstOrNull()?.userCouponId
                    ?: throw IllegalStateException("No user coupons found")

                // When
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId/coupons/$userCouponId"),
                    UserCouponResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { userCoupon ->
                    userCoupon.id shouldBe userCouponId
                    userCoupon.userId shouldBe userId
                    userCoupon.couponName shouldNotBe null
                    userCoupon.description shouldNotBe null
                    (userCoupon.discountRate > 0) shouldBe true
                    userCoupon.status shouldBe CouponStatus.AVAILABLE
                    userCoupon.issuedAt shouldNotBe null
                    userCoupon.expiresAt shouldNotBe null
                    userCoupon.canUse shouldBe true
                }
            }

            it("다른 사용자의 쿠폰을 조회할 수 없어야 한다") {
                // Given - user1의 쿠폰을 user2가 조회 시도
                val userId1 = user1Id!!
                val userId2 = user2Id!!

                // user1의 쿠폰 ID 가져오기
                val listResponse = restTemplate.getForEntity(
                    url("/coupons/users/$userId1"),
                    UserCouponListResponse::class.java
                )
                val userCouponId = listResponse.body?.coupons?.firstOrNull()?.userCouponId
                    ?: throw IllegalStateException("No user coupons found")

                // When - user2가 user1의 쿠폰 조회 시도
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId2/coupons/$userCouponId"),
                    String::class.java
                )

                // Then - 현재 구현은 userId로 필터링하여 404를 반환합니다
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("존재하지 않는 사용자 쿠폰 조회 시 404를 반환해야 한다") {
                // Given
                val userId = user1Id!!
                val invalidUserCouponId = UUID.randomUUID()

                // When
                val response = restTemplate.getForEntity(
                    url("/coupons/users/$userId/coupons/$invalidUserCouponId"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("복합 사용 시나리오") {
            it("쿠폰 목록 조회 후 발급, 보유 쿠폰 확인을 순차적으로 수행할 수 있어야 한다") {
                // Given - 새로운 사용자 생성
                val newUser = userService.createUser(CreateUserCommand(balance = 500000L))
                val userId = newUser.id!!

                // Step 1: 사용 가능한 쿠폰 목록 조회
                val availableResponse = restTemplate.getForEntity(
                    url("/coupons/available"),
                    AvailableCouponResponse::class.java
                )
                availableResponse.statusCode shouldBe HttpStatus.OK
                val couponId = availableResponse.body?.coupons?.first()?.id!!

                // Step 2: 쿠폰 상세 조회
                val detailResponse = restTemplate.getForEntity(
                    url("/coupons/$couponId"),
                    CouponDetailResponse::class.java
                )
                detailResponse.statusCode shouldBe HttpStatus.OK
                detailResponse.body?.isAvailable shouldBe true

                // Step 3: 쿠폰 발급
                val request = IssueCouponRequest(userId = userId)
                val issueResponse = restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request,
                    IssueCouponResponse::class.java
                )
                issueResponse.statusCode shouldBe HttpStatus.OK
                val userCouponId = issueResponse.body?.userCouponId!!

                // Step 4: 사용자 쿠폰 목록 확인
                val listResponse = restTemplate.getForEntity(
                    url("/coupons/users/$userId"),
                    UserCouponListResponse::class.java
                )
                listResponse.statusCode shouldBe HttpStatus.OK
                ((listResponse.body?.coupons?.size ?: 0) >= 1) shouldBe true

                // Step 5: 특정 쿠폰 상세 조회
                val userCouponResponse = restTemplate.getForEntity(
                    url("/coupons/users/$userId/coupons/$userCouponId"),
                    UserCouponResponse::class.java
                )
                userCouponResponse.statusCode shouldBe HttpStatus.OK
                userCouponResponse.body?.status shouldBe CouponStatus.AVAILABLE
            }

            it("여러 사용자가 동일한 쿠폰을 발급받을 수 있어야 한다") {
                // Given - 새로운 사용자 2명 생성
                val newUser1 = userService.createUser(CreateUserCommand(balance = 500000L))
                val newUser2 = userService.createUser(CreateUserCommand(balance = 500000L))
                val userId1 = newUser1.id!!
                val userId2 = newUser2.id!!
                val couponId = availableCouponId!!

                // When - 첫 번째 사용자 발급
                val request1 = IssueCouponRequest(userId = userId1)
                val response1 = restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request1,
                    IssueCouponResponse::class.java
                )

                // When - 두 번째 사용자 발급
                val request2 = IssueCouponRequest(userId = userId2)
                val response2 = restTemplate.postForEntity(
                    url("/coupons/$couponId/issue"),
                    request2,
                    IssueCouponResponse::class.java
                )

                // Then
                response1.statusCode shouldBe HttpStatus.OK
                response2.statusCode shouldBe HttpStatus.OK
                response1.body?.userId shouldBe userId1
                response2.body?.userId shouldBe userId2

                // 각 사용자 쿠폰 목록 확인
                val list1 = restTemplate.getForEntity(
                    url("/coupons/users/$userId1"),
                    UserCouponListResponse::class.java
                )
                val list2 = restTemplate.getForEntity(
                    url("/coupons/users/$userId2"),
                    UserCouponListResponse::class.java
                )

                ((list1.body?.coupons?.size ?: 0) >= 1) shouldBe true
                ((list2.body?.coupons?.size ?: 0) >= 1) shouldBe true
            }
        }
    }
})
