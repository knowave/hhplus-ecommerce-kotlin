package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.repository.UserJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import java.util.UUID

class UserServiceUnitTest : DescribeSpec({
    lateinit var userRepository: UserJpaRepository
    lateinit var userService: UserServiceImpl

    beforeEach {
        userRepository = mockk(relaxed = true)
        userService = UserServiceImpl(userRepository)
    }

    describe("UserService 단위 테스트 - chargeBalance") {
        context("정상 케이스") {
            it("유효한 금액으로 잔액을 정상적으로 충전한다") {
                // given
                val initialBalance = 50000L
                val chargeAmount = 100000L
                val expectedBalance = initialBalance + chargeAmount
                val userId = UUID.randomUUID()
                
                // Mock 유저 생성 - find에서는 충전 전 상태, save에서는 충전 후 상태 반환
                val userBeforeCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns initialBalance
                }
                val userAfterCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns expectedBalance
                    every { updatedAt } returns mockk {
                        every { format(any()) } returns "2024-01-01T00:00:00"
                    }
                }

                every { userRepository.findById(userId) } returns Optional.of(userBeforeCharge)
                every { userRepository.save(any()) } returns userAfterCharge

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
                val initialBalance = 50000L
                val chargeAmount = 1_000L
                val userId = UUID.randomUUID()
                
                val userBeforeCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns initialBalance
                }
                val userAfterCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns (initialBalance + chargeAmount)
                    every { updatedAt } returns mockk {
                        every { format(any()) } returns "2024-01-01T00:00:00"
                    }
                }

                every { userRepository.findById(userId) } returns Optional.of(userBeforeCharge)
                every { userRepository.save(any()) } returns userAfterCharge

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
                val initialBalance = 50000L
                val chargeAmount = 1_000_000L
                val userId = UUID.randomUUID()
                
                val userBeforeCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns initialBalance
                }
                val userAfterCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns (initialBalance + chargeAmount)
                    every { updatedAt } returns mockk {
                        every { format(any()) } returns "2024-01-01T00:00:00"
                    }
                }

                every { userRepository.findById(userId) } returns Optional.of(userBeforeCharge)
                every { userRepository.save(any()) } returns userAfterCharge

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
                val invalidAmount = 500L
                val userId = UUID.randomUUID()
                
                val user = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns 50000L
                }

                every { userRepository.findById(userId) } returns Optional.of(user)

                // when & then
                val exception = shouldThrow<InvalidAmountException> {
                    userService.chargeBalance(userId, invalidAmount)
                }
                exception.message shouldContain "must be at least 1000 won."

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("충전 금액이 0원일 때 InvalidAmountException을 발생시킨다") {
                // given
                val invalidAmount = 0L
                val userId = UUID.randomUUID()
                
                val user = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns 50000L
                }

                every { userRepository.findById(userId) } returns Optional.of(user)

                // when & then
                shouldThrow<InvalidAmountException> {
                    userService.chargeBalance(userId, invalidAmount)
                }

                verify(exactly = 1) { userRepository.findById(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("충전 금액이 최대 금액(3,000,000원)을 초과할 때 InvalidAmountException을 발생시킨다") {
                // given
                val invalidAmount = 3_500_000L
                val userId = UUID.randomUUID()
                
                val user = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns 50000L
                }

                every { userRepository.findById(userId) } returns Optional.of(user)

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
                val initialBalance = 9_500_000L
                val chargeAmount = 1_000_000L
                val userId = UUID.randomUUID()
                
                val user = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns initialBalance
                }

                every { userRepository.findById(userId) } returns Optional.of(user)

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
                val initialBalance = 9_000_000L
                val chargeAmount = 1_000_000L
                val userId = UUID.randomUUID()
                
                val userBeforeCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns initialBalance
                }
                val userAfterCharge = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { balance } returns 10_000_000L
                    every { updatedAt } returns mockk {
                        every { format(any()) } returns "2024-01-01T00:00:00"
                    }
                }

                every { userRepository.findById(userId) } returns Optional.of(userBeforeCharge)
                every { userRepository.save(any()) } returns userAfterCharge

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
                val userId = UUID.randomUUID()
                val chargeAmount = 10000L

                every { userRepository.findById(userId) } returns Optional.empty()

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
                val balance = 50000L
                val userId = UUID.randomUUID()
                
                val user = mockk<User>(relaxed = true) {
                    every { id } returns userId
                    every { this@mockk.balance } returns balance
                    every { createdAt } returns mockk()
                    every { updatedAt } returns mockk()
                }

                every { userRepository.findById(userId) } returns Optional.of(user)

                // when
                val result = userService.getUser(userId)

                // then
                result.id shouldBe userId
                result.balance shouldBe balance
                result.createdAt shouldNotBe null
                result.updatedAt shouldNotBe null

                verify(exactly = 1) { userRepository.findById(userId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 사용자 정보 조회 시 UserNotFoundException을 발생시킨다") {
                // given
                val userId = UUID.randomUUID()
                every { userRepository.findById(userId) } returns Optional.empty()

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
                val request = CreateUserCommand(balance = initialBalance)
                val now = java.time.LocalDateTime.now()

                every { userRepository.save(any()) } answers { 
                    val user = firstArg<User>()
                    val createdAtField = user.javaClass.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(user, now)
                    val updatedAtField = user.javaClass.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(user, now)
                    user
                }

                // when
                val result = userService.createUser(request)

                // then
                result.balance shouldBe initialBalance
                result.createdAt shouldNotBe null
                result.updatedAt shouldNotBe null

                verify(exactly = 1) { userRepository.save(any()) }
            }

            it("최소 금액(1,000원)으로 사용자를 생성할 수 있다") {
                // given
                val minBalance = 1_000L
                val request = CreateUserCommand(balance = minBalance)
                val now = java.time.LocalDateTime.now()

                every { userRepository.save(any()) } answers { 
                    val user = firstArg<User>()
                    val createdAtField = user.javaClass.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(user, now)
                    val updatedAtField = user.javaClass.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(user, now)
                    user
                }

                // when
                val result = userService.createUser(request)

                // then
                result.balance shouldBe minBalance
                verify(exactly = 1) { userRepository.save(any()) }
            }

            it("최대 금액(1,000,000원)으로 사용자를 생성할 수 있다") {
                // given
                val maxBalance = 1_000_000L
                val request = CreateUserCommand(balance = maxBalance)
                val now = java.time.LocalDateTime.now()

                every { userRepository.save(any()) } answers { 
                    val user = firstArg<User>()
                    val createdAtField = user.javaClass.superclass.getDeclaredField("createdAt")
                    createdAtField.isAccessible = true
                    createdAtField.set(user, now)
                    val updatedAtField = user.javaClass.superclass.getDeclaredField("updatedAt")
                    updatedAtField.isAccessible = true
                    updatedAtField.set(user, now)
                    user
                }

                // when
                val result = userService.createUser(request)

                // then
                result.balance shouldBe maxBalance
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
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }
})
