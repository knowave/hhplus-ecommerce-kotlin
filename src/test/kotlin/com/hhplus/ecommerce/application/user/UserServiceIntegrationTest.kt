package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.BalanceLimitExceededException
import com.hhplus.ecommerce.common.exception.InvalidAmountException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.user.repository.UserRepository
import com.hhplus.ecommerce.infrastructure.user.UserRepositoryImpl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.util.UUID

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
class UserServiceIntegrationTest(
    private val userRepository: UserRepository,
    private val userService: UserService
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    init {
        describe("UserService 통합 테스트 - Service와 Repository 통합") {
            context("사용자 생성 및 조회 통합 시나리오") {
                it("사용자를 생성하고 조회할 수 있다") {
                    // given
                    val request = CreateUserCommand(balance = 50000L)

                    // when - 사용자 생성
                    val createdUser = userService.createUser(request)

                    // then - 생성 결과 검증
                    createdUser.id shouldNotBe null
                    createdUser.balance shouldBe 50000L

                    // when - 생성된 사용자 조회
                    val userInfo = userService.getUser(createdUser.id!!)

                    // then - 조회 결과 검증
                    userInfo.id shouldBe createdUser.id
                    userInfo.balance shouldBe 50000L
                }

                it("사용자를 생성하고 잔액을 조회할 수 있다") {
                    // given
                    val request = CreateUserCommand(balance =30000L)

                    // when - 사용자 생성
                    val createdUser = userService.createUser(request)

                    // when - 잔액 조회
                    val balanceResponse = userService.getUser(createdUser.id!!)

                    // then
                    balanceResponse.id shouldBe createdUser.id
                    balanceResponse.balance shouldBe 30000L
                }
            }

            context("잔액 충전 통합 시나리오") {
                it("사용자를 생성하고 잔액을 충전할 수 있다") {
                    // given - 사용자 생성
                    val request = CreateUserCommand(balance = 10000L)
                    val createdUser = userService.createUser(request)

                    // when - 잔액 충전
                    val chargeAmount = 50000L
                    val chargeResponse = userService.chargeBalance(createdUser.id!!, chargeAmount)

                    // then - 충전 결과 검증
                    chargeResponse.userId shouldBe createdUser.id
                    chargeResponse.previousBalance shouldBe 10000L
                    chargeResponse.chargedAmount shouldBe 50000L
                    chargeResponse.currentBalance shouldBe 60000L

                    // when - 충전 후 잔액 재조회
                    val updatedBalance = userService.getUser(createdUser.id!!)

                    // then - 잔액이 실제로 증가했는지 확인
                    updatedBalance.balance shouldBe 60000L
                }

                it("여러 번 충전할 수 있다") {
                    // given - 사용자 생성
                    val request = CreateUserCommand(balance = 10000L)
                    val createdUser = userService.createUser(request)

                    // when - 첫 번째 충전
                    userService.chargeBalance(createdUser.id!!, 20000L)

                    // when - 두 번째 충전
                    userService.chargeBalance(createdUser.id!!, 30000L)

                    // when - 세 번째 충전
                    val finalCharge = userService.chargeBalance(createdUser.id!!, 40000L)

                    // then - 총 잔액 검증 (10000 + 20000 + 30000 + 40000)
                    finalCharge.currentBalance shouldBe 100000L

                    // when - 최종 잔액 조회
                    val finalBalance = userService.getUser(createdUser.id!!)

                    // then
                    finalBalance.balance shouldBe 100000L
                }
            }

            context("기존 사용자 데이터 조회 및 충전") {
                it("초기 데이터로 저장된 사용자의 잔액을 조회할 수 있다") {
                    // given - UserRepositoryImpl에 초기 데이터로 id=1 사용자가 있음
                    val request = CreateUserCommand(balance = 10000L)
                    val user = userService.createUser(request)

                    // when
                    val userBalance = userService.getUser(user.id!!)

                    // then
                    userBalance.id shouldBe 1L
                    userBalance.balance shouldBe 50000L
                }

                it("초기 데이터 사용자의 잔액을 충전할 수 있다") {
                    // given - UserRepositoryImpl에 초기 데이터로 id=1 사용자가 있음
                    val request = CreateUserCommand(balance = 10000L)
                    val user = userService.createUser(request)
                    val initialBalance = userService.getUser(user.id!!).balance

                    // when - 잔액 충전
                    val chargeResponse = userService.chargeBalance(user.id!!, 100000L)

                    // then
                    chargeResponse.previousBalance shouldBe initialBalance
                    chargeResponse.currentBalance shouldBe (initialBalance + 100000L)

                    // when - 재조회
                    val updatedBalance = userService.getUser(user.id!!)

                    // then - 잔액이 영구적으로 변경되었는지 확인
                    updatedBalance.balance shouldBe (initialBalance + 100000L)
                }
            }

            context("예외 케이스 통합 시나리오") {
                it("존재하지 않는 사용자 조회 시 예외가 발생한다") {
                    // given
                    val nonExistentId = UUID.randomUUID()

                    // when & then
                    shouldThrow<UserNotFoundException> {
                        userService.getUser(nonExistentId)
                    }

                    shouldThrow<UserNotFoundException> {
                        userService.getUser(nonExistentId)
                    }

                    shouldThrow<UserNotFoundException> {
                        userService.chargeBalance(nonExistentId, 10000L)
                    }
                }

                it("잘못된 금액으로 사용자 생성 시 예외가 발생한다") {
                    // given
                    val invalidRequest = CreateUserCommand(balance = 500L)

                    // when & then
                    shouldThrow<InvalidAmountException> {
                        userService.createUser(invalidRequest)
                    }

                    // 사용자가 생성되지 않았는지 확인
                    val allUsers = userRepository.findAll()
                    allUsers.none { it.balance == 500L } shouldBe true
                }

                it("잘못된 금액으로 충전 시 예외가 발생하고 잔액이 변하지 않는다") {
                    // given - 사용자 생성
                    val request = CreateUserCommand(balance = 50000L)
                    val createdUser = userService.createUser(request)
                    val initialBalance = userService.getUser(createdUser.id!!).balance

                    // when & then - 잘못된 충전 시도
                    shouldThrow<InvalidAmountException> {
                        userService.chargeBalance(createdUser.id!!, 500L)
                    }

                    // then - 잔액이 변하지 않았는지 확인
                    val unchangedBalance = userService.getUser(createdUser.id!!)
                    unchangedBalance.balance shouldBe initialBalance
                }

                it("잔액 한도 초과 시 예외가 발생하고 잔액이 변하지 않는다") {
                    // given - 높은 초기 잔액으로 사용자 생성
                    val request = CreateUserCommand(balance =1_000_000L)
                    val createdUser = userService.createUser(request)

                    // when - 한도 내에서 충전
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 2,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 3,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 4,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 5,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 6,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 7,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 8,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 9,000,000
                    userService.chargeBalance(createdUser.id!!, 500_000L) // 총 9,500,000

                    val currentBalance = userService.getUser(createdUser.id!!).balance
                    currentBalance shouldBe 9_500_000L

                    // when & then - 한도 초과 충전 시도 (9,500,000 + 1,000,000 = 10,500,000 > 10,000,000)
                    shouldThrow<BalanceLimitExceededException> {
                        userService.chargeBalance(createdUser.id!!, 1_000_000L)
                    }

                    // then - 잔액이 변하지 않았는지 확인
                    val unchangedBalance = userService.getUser(createdUser.id!!)
                    unchangedBalance.balance shouldBe 9_500_000L
                }

                it("최대 한도까지는 충전이 가능하다") {
                    // given - 사용자 생성
                    val request = CreateUserCommand(balance =1_000_000L)
                    val createdUser = userService.createUser(request)

                    // when - 최대 한도(10,000,000)까지 충전
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 2,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 3,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 4,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 5,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 6,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 7,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 8,000,000
                    userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 9,000,000
                    val finalCharge = userService.chargeBalance(createdUser.id!!, 1_000_000L) // 총 10,000,000

                    // then
                    finalCharge.currentBalance shouldBe 10_000_000L

                    val finalBalance = userService.getUser(createdUser.id!!)
                    finalBalance.balance shouldBe 10_000_000L
                }
            }
        }
    }
}