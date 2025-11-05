package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.domain.user.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserServiceUnitTest : DescribeSpec({
    lateinit var userRepository: UserRepository
    lateinit var userService: UserServiceImpl
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        userRepository = mockk(relaxed = true)
        userService = UserServiceImpl(userRepository)
    }

    describe("UserService 단위 테스트 - chargeBalance") {
        context("정상 케이스") {
            it("유효한 금액으로 잔액을 정상적으로 충전한다") {
                // given
                val userId = 1L
                val initialBalance = 50000L
                val chargeAmount = 100000L
                val expectedBalance = initialBalance + chargeAmount
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = initialBalance,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(any()) } returns user

                // when
                val result = userService.chargeBalance(userId, chargeAmount)

                // then
                result.userId shouldBe userId
                result.previousBalance shouldBe initialBalance
                result.chargedAmount shouldBe chargeAmount
                result.currentBalance shouldBe expectedBalance
                result.chargedAt shouldNotBe null

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { userRepository.save(any()) }
            }

            it("최소 금액(1,000원)으로 충전할 수 있다") {
                // given
                val userId = 1L
                val initialBalance = 50000L
                val chargeAmount = 1_000L
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = initialBalance,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(any()) } returns user

                // when
                val result = userService.chargeBalance(userId, chargeAmount)

                // then
                result.chargedAmount shouldBe chargeAmount
                result.currentBalance shouldBe (initialBalance + chargeAmount)

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { userRepository.save(any()) }
            }

            it("최대 금액(1,000,000원)으로 충전할 수 있다") {
                // given
                val userId = 1L
                val initialBalance = 50000L
                val chargeAmount = 1_000_000L
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = initialBalance,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(any()) } returns user

                // when
                val result = userService.chargeBalance(userId, chargeAmount)

                // then
                result.chargedAmount shouldBe chargeAmount
                result.currentBalance shouldBe (initialBalance + chargeAmount)

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { userRepository.save(any()) }
            }
        }

        context("예외 케이스 - 충전 금액 검증") {
            it("충전 금액이 최소 금액(1,000원) 미만일 때 InvalidAmountException을 발생시킨다") {
                // given
                val userId = 1L
                val invalidAmount = 500L
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = 50000L,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(user) } returns user

                // when & then
                val exception = shouldThrow<InvalidAmountException> {
                    userService.chargeBalance(userId, invalidAmount)
                }
                exception.message shouldContain "1000원 이상"

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("충전 금액이 0원일 때 InvalidAmountException을 발생시킨다") {
                // given
                val userId = 1L
                val invalidAmount = 0L
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = 50000L,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(user) } returns user

                // when & then
                shouldThrow<InvalidAmountException> {
                    userService.chargeBalance(userId, invalidAmount)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("충전 금액이 최대 금액(3,000,000원)을 초과할 때 InvalidAmountException을 발생시킨다") {
                // given
                val userId = 1L
                val invalidAmount = 3_500_000L
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = 50000L,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(user) } returns user

                // when & then
                shouldThrow<InvalidAmountException> {
                    userService.chargeBalance(userId, invalidAmount)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }

        context("예외 케이스 - 잔액 한도 검증") {
            it("충전 후 잔액이 최대 한도(10,000,000원)를 초과할 때 BalanceLimitExceededException을 발생시킨다") {
                // given
                val userId = 1L
                val initialBalance = 9_500_000L
                val chargeAmount = 1_000_000L // 충전 후 10,500,000원이 되어 한도 초과
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = initialBalance,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user

                // when & then
                val exception = shouldThrow<BalanceLimitExceededException> {
                    userService.chargeBalance(userId, chargeAmount)
                }
                exception.message shouldContain "Balance limit exceeded"

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("충전 후 잔액이 정확히 최대 한도(10,000,000원)일 때는 정상 처리된다") {
                // given
                val userId = 1L
                val initialBalance = 9_000_000L
                val chargeAmount = 1_000_000L // 충전 후 정확히 10,000,000원
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = initialBalance,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user
                every { userRepository.save(any()) } returns user

                // when
                val result = userService.chargeBalance(userId, chargeAmount)

                // then
                result.currentBalance shouldBe 10_000_000L

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 1) { userRepository.save(any()) }
            }
        }

        context("예외 케이스 - 사용자 미존재") {
            it("존재하지 않는 사용자에게 충전 시 UserNotFoundException을 발생시킨다") {
                // given
                val userId = 999L
                val chargeAmount = 10000L

                every { userRepository.findById(userId) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    userService.chargeBalance(userId, chargeAmount)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    describe("UserService 단위 테스트 - getUserInfo") {
        context("정상 케이스") {
            it("사용자 ID로 사용자 정보를 정상적으로 조회한다") {
                // given
                val userId = 1L
                val balance = 50000L
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = userId,
                    balance = balance,
                    createdAt = now,
                    updatedAt = now
                )

                every { userRepository.findById(userId) } returns user

                // when
                val result = userService.getUser(userId)

                // then
                result.id shouldBe userId
                result.balance shouldBe balance
                result.createdAt shouldBe now
                result.updatedAt shouldBe now

                verify(exactly = 1) { userRepository.findById(userId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자 정보 조회 시 UserNotFoundException을 발생시킨다") {
                // given
                val userId = 999L
                every { userRepository.findById(userId) } returns null

                // when & then
                shouldThrow<UserNotFoundException> {
                    userService.getUser(userId)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
            }
        }
    }

    describe("UserService 단위 테스트 - createUser") {
        context("정상 케이스") {
            it("유효한 초기 잔액으로 사용자를 정상적으로 생성한다") {
                // given
                val initialBalance = 10000L
                val generatedId = 1L
                val request = CreateUserCommand(balance = initialBalance)

                every { userRepository.generateId() } returns generatedId
                every { userRepository.save(any()) } answers { firstArg() }

                // when
                val result = userService.createUser(request)

                // then
                result.id shouldBe generatedId
                result.balance shouldBe initialBalance
                result.createdAt shouldNotBe null
                result.updatedAt shouldNotBe null
                result.createdAt shouldBe result.updatedAt

                verify(exactly = 1) { userRepository.generateId() }
                verify(exactly = 1) { userRepository.save(any()) }
            }

            it("최소 금액(1,000원)으로 사용자를 생성할 수 있다") {
                // given
                val minBalance = 1_000L
                val generatedId = 1L
                val request = CreateUserCommand(balance = minBalance)

                every { userRepository.generateId() } returns generatedId
                every { userRepository.save(any()) } answers { firstArg() }

                // when
                val result = userService.createUser(request)

                // then
                result.id shouldBe generatedId
                result.balance shouldBe minBalance

                verify(exactly = 1) { userRepository.generateId() }
                verify(exactly = 1) { userRepository.save(any()) }
            }

            it("최대 금액(1,000,000원)으로 사용자를 생성할 수 있다") {
                // given
                val maxBalance = 1_000_000L
                val generatedId = 1L
                val request = CreateUserCommand(balance = maxBalance)

                every { userRepository.generateId() } returns generatedId
                every { userRepository.save(any()) } answers { firstArg() }

                // when
                val result = userService.createUser(request)

                // then
                result.id shouldBe generatedId
                result.balance shouldBe maxBalance

                verify(exactly = 1) { userRepository.generateId() }
                verify(exactly = 1) { userRepository.save(any()) }
            }
        }

        context("예외 케이스") {
            it("초기 잔액이 최소 금액(1,000원) 미만일 때 InvalidAmountException을 발생시킨다") {
                // given
                val invalidBalance = 500L
                val request = CreateUserCommand(balance = invalidBalance)

                // when & then
                shouldThrow<InvalidAmountException> {
                    userService.createUser(request)
                }

                verify(exactly = 0) { userRepository.generateId() }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("초기 잔액이 0원일 때 InvalidAmountException을 발생시킨다") {
                // given
                val invalidBalance = 0L
                val request = CreateUserCommand(balance = invalidBalance)

                // when & then
                shouldThrow<InvalidAmountException> {
                    userService.createUser(request)
                }

                verify(exactly = 0) { userRepository.generateId() }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("초기 잔액이 최대 금액(3,000,000원)을 초과할 때 InvalidAmountException을 발생시킨다") {
                // given
                val invalidBalance = 3_500_000L
                val request = CreateUserCommand(balance = invalidBalance)

                // when & then
                shouldThrow<InvalidAmountException> {
                    userService.createUser(request)
                }

                verify(exactly = 0) { userRepository.generateId() }
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }
})