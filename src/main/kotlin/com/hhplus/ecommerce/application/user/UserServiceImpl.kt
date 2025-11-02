package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.common.exception.BalanceLimitExceededException
import com.hhplus.ecommerce.common.exception.InvalidAmountException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.infrastructure.user.UserRepository
import com.hhplus.ecommerce.presentation.user.dto.*
import com.hhplus.ecommerce.model.user.User
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
        private const val MAX_CHARGE_AMOUNT = 1_000_000L
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun getUserBalance(userId: Long): UserBalanceResponse {
        val user = findUserById(userId)

        return UserBalanceResponse(
            userId = user.id,
            balance = user.balance,
        )
    }

    override fun chargeBalance(userId: Long, amount: Long): ChargeBalanceResponse {
        val user = findUserById(userId)

        // 충전 금액 유효성 검증
        validateChargeAmount(amount)

        // 잔액 계산 및 한도 체크
        val previousBalance = user.balance
        val newBalance = previousBalance + amount
        validateBalanceLimit(newBalance)

        // 잔액 업데이트
        val chargedAt = LocalDateTime.now().format(DATE_FORMATTER)
        user.balance = newBalance
        user.updatedAt = chargedAt

        // Repository에 저장
        userRepository.save(user)

        // 응답 생성
        return ChargeBalanceResponse(
            userId = userId,
            previousBalance = previousBalance,
            chargedAmount = amount,
            currentBalance = newBalance,
            chargedAt = chargedAt
        )
    }

    override fun getUserInfo(userId: Long): UserInfoResponse {
        val user = findUserById(userId)

        return UserInfoResponse(
            userId = user.id,
            balance = user.balance,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    override fun createUser(dto: CreateUserRequest): User {
        // 초기 잔액 유효성 검증
        validateChargeAmount(dto.balance)

        // 현재 시간
        val now = LocalDateTime.now().format(DATE_FORMATTER)

        // 새 사용자 생성
        val newUser = User(
            id = userRepository.generateId(),
            balance = dto.balance,
            createdAt = now,
            updatedAt = now
        )

        // Repository에 저장
        return userRepository.save(newUser)
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
                    "충전 금액은 ${MIN_CHARGE_AMOUNT}원 이상이어야 합니다. (입력값: ${amount}원)"
                )
            }
            amount > MAX_CHARGE_AMOUNT -> {
                throw InvalidAmountException(
                    "충전 금액은 ${MAX_CHARGE_AMOUNT}원 이하여야 합니다. (입력값: ${amount}원)"
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