package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.application.user.dto.*
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.domain.user.entity.User
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {

    companion object {
        private const val MAX_BALANCE = 10_000_000L
        private const val MIN_CHARGE_AMOUNT = 1_000L
        private const val MAX_CHARGE_AMOUNT = 3_000_000L
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun chargeBalance(userId: Long, amount: Long): ChargeBalanceResult {
        val user = findUserById(userId)

        // 충전 금액 유효성 검증
        validateChargeAmount(amount)

        // 잔액 계산 및 한도 체크
        val previousBalance = user.balance
        val newBalance = previousBalance + amount
        validateBalanceLimit(newBalance)

        // 잔액 업데이트 (updatedAt은 JPA Auditing이 자동으로 설정)
        user.balance = newBalance

        // Repository에 저장
        val savedUser = userRepository.save(user)

        // 응답 생성
        return ChargeBalanceResult(
            userId = userId,
            previousBalance = previousBalance,
            chargedAmount = amount,
            currentBalance = newBalance,
            chargedAt = savedUser.updatedAt!!.format(DATE_FORMATTER)
        )
    }

    override fun getUser(id: Long): User {
        return findUserById(id)
    }

    override fun createUser(dto: CreateUserCommand): User {
        // 초기 잔액 유효성 검증
        validateChargeAmount(dto.balance)

        // 새 사용자 생성 (id, createdAt, updatedAt은 JPA가 자동으로 설정)
        val newUser = User(
            balance = dto.balance
        )

        // Repository에 저장
        return userRepository.save(newUser)
    }

    override fun updateUser(user: User): User {
        return userRepository.save(user)
    }

    private fun findUserById(userId: Long): User {
        return userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)
    }

    // 충전 금액의 유효성을 검증
    private fun validateChargeAmount(amount: Long) {
        when {
            amount < MIN_CHARGE_AMOUNT -> {
                throw InvalidAmountException(
                    "The recharge amount must be at least ${MIN_CHARGE_AMOUNT} won. (value: ${amount})"
                )
            }
            amount > MAX_CHARGE_AMOUNT -> {
                throw InvalidAmountException(
                    "The recharge amount must be less than ${MAX_CHARGE_AMOUNT} won. (value: ${amount})"
                )
            }
        }
    }

    // 잔액 한도를 검증
    private fun validateBalanceLimit(balance: Long) {
        if (balance > MAX_BALANCE) {
            throw BalanceLimitExceededException(
                limit = MAX_BALANCE,
                attempted = balance
            )
        }
    }
}