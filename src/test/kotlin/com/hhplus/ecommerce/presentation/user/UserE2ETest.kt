package com.hhplus.ecommerce.presentation.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import com.hhplus.ecommerce.presentation.user.dto.CreateUserRequest
import com.hhplus.ecommerce.presentation.user.dto.ChargeBalanceRequest
import com.hhplus.ecommerce.presentation.user.dto.UserBalanceResponse
import com.hhplus.ecommerce.presentation.user.dto.ChargeBalanceResponse
import com.hhplus.ecommerce.presentation.user.dto.UserInfoResponse
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.UserRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val userRepository: UserRepository
) : DescribeSpec({

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    afterEach {
        userRepository.clear()
    }

    describe("User API E2E Tests") {
        
        describe("사용자 생성 및 조회") {
            it("사용자를 생성할 수 있어야 한다") {
                // Given
                val request = CreateUserRequest(balance = 100000L)

                // When
                val response = restTemplate.postForEntity(url("/users"), request, User::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body shouldNotBe null
                response.body?.let { user ->
                    user.id shouldNotBe null
                    user.balance shouldBe 100000L
                }
            }

            it("생성한 사용자의 정보를 조회할 수 있어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 50000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                // When
                val getResponse = restTemplate.getForEntity(url("/users/$userId"), UserInfoResponse::class.java)

                // Then
                getResponse.statusCode shouldBe HttpStatus.OK
                getResponse.body shouldNotBe null
                getResponse.body?.let { userInfo ->
                    userInfo.userId shouldBe userId
                    userInfo.balance shouldBe 50000L
                }
            }
        }

        describe("사용자 잔액 조회") {
            it("사용자의 잔액을 조회할 수 있어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 75000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                // When
                val balanceResponse = restTemplate.getForEntity(url("/users/$userId/balance"), UserBalanceResponse::class.java)

                // Then
                balanceResponse.statusCode shouldBe HttpStatus.OK
                balanceResponse.body shouldNotBe null
                balanceResponse.body?.let { balance ->
                    balance.userId shouldBe userId
                    balance.balance shouldBe 75000L
                }
            }

            it("존재하지 않는 사용자의 잔액 조회 시 404를 반환해야 한다") {
                // When
                val response = restTemplate.getForEntity(url("/users/999999/balance"), String::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("사용자 잔액 충전") {
            it("사용자의 잔액을 충전할 수 있어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 100000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                val chargeRequest = ChargeBalanceRequest(balance = 50000L)

                // When
                val chargeResponse = restTemplate.postForEntity(
                    url("/users/$userId/balance/charge"),
                    chargeRequest,
                    ChargeBalanceResponse::class.java
                )

                // Then
                chargeResponse.statusCode shouldBe HttpStatus.OK
                chargeResponse.body shouldNotBe null
                chargeResponse.body?.let { response ->
                    response.userId shouldBe userId
                    response.previousBalance shouldBe 100000L
                    response.chargedAmount shouldBe 50000L
                    response.currentBalance shouldBe 150000L
                    response.chargedAt shouldNotBe null
                }
            }

            it("충전 후 사용자의 잔액이 정확히 반영되어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 100000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                val chargeRequest = ChargeBalanceRequest(balance = 50000L)
                restTemplate.postForEntity(
                    url("/users/$userId/balance/charge"),
                    chargeRequest,
                    ChargeBalanceResponse::class.java
                )

                // When
                val balanceResponse = restTemplate.getForEntity(url("/users/$userId/balance"), UserBalanceResponse::class.java)

                // Then
                balanceResponse.statusCode shouldBe HttpStatus.OK
                balanceResponse.body?.balance shouldBe 150000L
            }

            it("최소 충전 금액(1000원)보다 작은 금액으로 충전할 수 없어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 50000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                val invalidChargeRequest = ChargeBalanceRequest(balance = 500L)

                // When
                val chargeResponse = restTemplate.postForEntity(
                    url("/users/$userId/balance/charge"),
                    invalidChargeRequest,
                    String::class.java
                )

                // Then
                chargeResponse.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("최대 충전 금액(3000000원)을 초과하는 금액으로 충전할 수 없어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 50000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                val invalidChargeRequest = ChargeBalanceRequest(balance = 3100000L)

                // When
                val chargeResponse = restTemplate.postForEntity(
                    url("/users/$userId/balance/charge"),
                    invalidChargeRequest,
                    String::class.java
                )

                // Then
                chargeResponse.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        describe("복합 사용 시나리오") {
            it("사용자 생성 후 여러 번 충전할 수 있어야 한다") {
                // Given
                val createRequest = CreateUserRequest(balance = 100000L)
                val createResponse = restTemplate.postForEntity(url("/users"), createRequest, User::class.java)
                val userId = createResponse.body?.id

                // When & Then - 첫 번째 충전
                val chargeRequest1 = ChargeBalanceRequest(balance = 50000L)
                val chargeResponse1 = restTemplate.postForEntity(
                    url("/users/$userId/balance/charge"),
                    chargeRequest1,
                    ChargeBalanceResponse::class.java
                )
                chargeResponse1.statusCode shouldBe HttpStatus.OK
                chargeResponse1.body?.currentBalance shouldBe 150000L

                // When & Then - 두 번째 충전
                val chargeRequest2 = ChargeBalanceRequest(balance = 100000L)
                val chargeResponse2 = restTemplate.postForEntity(
                    url("/users/$userId/balance/charge"),
                    chargeRequest2,
                    ChargeBalanceResponse::class.java
                )
                chargeResponse2.statusCode shouldBe HttpStatus.OK
                chargeResponse2.body?.previousBalance shouldBe 150000L
                chargeResponse2.body?.currentBalance shouldBe 250000L

                // When & Then - 최종 잔액 확인
                val balanceResponse = restTemplate.getForEntity(url("/users/$userId/balance"), UserBalanceResponse::class.java)
                balanceResponse.body?.balance shouldBe 250000L
            }

            it("여러 사용자를 독립적으로 관리할 수 있어야 한다") {
                // Given - 첫 번째 사용자 생성
                val createRequest1 = CreateUserRequest(balance = 100000L)
                val user1Response = restTemplate.postForEntity(url("/users"), createRequest1, User::class.java)
                val userId1 = user1Response.body?.id

                // Given - 두 번째 사용자 생성
                val createRequest2 = CreateUserRequest(balance = 200000L)
                val user2Response = restTemplate.postForEntity(url("/users"), createRequest2, User::class.java)
                val userId2 = user2Response.body?.id

                // When & Then - 첫 번째 사용자 충전
                val chargeRequest1 = ChargeBalanceRequest(balance = 50000L)
                restTemplate.postForEntity(
                    url("/users/$userId1/balance/charge"),
                    chargeRequest1,
                    ChargeBalanceResponse::class.java
                )

                // When & Then - 첫 번째 사용자 확인
                val balance1 = restTemplate.getForEntity(url("/users/$userId1/balance"), UserBalanceResponse::class.java)
                balance1.body?.balance shouldBe 150000L

                // When & Then - 두 번째 사용자 확인 (변화 없음)
                val balance2 = restTemplate.getForEntity(url("/users/$userId2/balance"), UserBalanceResponse::class.java)
                balance2.body?.balance shouldBe 200000L
            }
        }
    }
})
