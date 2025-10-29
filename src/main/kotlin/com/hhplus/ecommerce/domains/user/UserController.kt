package com.hhplus.ecommerce.domains.user

import com.hhplus.ecommerce.domains.user.dto.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MAX_BALANCE = 10_000_000L
private const val MIN_CHARGE_AMOUNT = 1_000L
private const val MAX_CHARGE_AMOUNT = 1_000_000L

private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
private val USERS: MutableMap<Long, User> = mutableMapOf(
    1L to User(
        userId = 1L,
        email = "user1@example.com",
        name = "홍길동",
        balance = 50000L,
        createdAt = "2025-01-01T00:00:00",
        updatedAt = "2025-10-29T10:30:00"
    ),
    2L to User(
        userId = 2L,
        email = "user2@example.com",
        name = "김철수",
        balance = 100000L,
        createdAt = "2025-01-15T00:00:00",
        updatedAt = "2025-10-29T09:00:00"
    ),
    3L to User(
        userId = 3L,
        email = "user3@example.com",
        name = "이영희",
        balance = 25000L,
        createdAt = "2025-02-01T00:00:00",
        updatedAt = "2025-10-28T15:20:00"
    )
)

@RestController
@RequestMapping("/api/users")
class UserController {

    // 사용자 잔액 조회
    @GetMapping("/{userId}/balance")
    fun getUserBalance(@PathVariable userId: Long): ResponseEntity<UserBalanceResponse> {
        val user = USERS[userId]
            ?: return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.",
                "/api/users/$userId/balance"
            )

        val response = UserBalanceResponse(
            userId = user.userId,
            balance = user.balance,
            currency = "KRW",
            lastUpdatedAt = user.updatedAt
        )

        return ResponseEntity.ok(response)
    }

    // 사용자 잔액 충전
    @PostMapping("/{userId}/balance/charge")
    fun chargeBalance(
        @PathVariable userId: Long,
        @RequestBody request: ChargeBalanceRequest
    ): ResponseEntity<ChargeBalanceResponse> {
        val user = USERS[userId]
            ?: return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.",
                "/api/users/$userId/balance/charge"
            )

        val amount = request.amount

        // 금액 유효성 검증
        if (amount < MIN_CHARGE_AMOUNT) {
            return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_AMOUNT",
                "충전 금액은 ${MIN_CHARGE_AMOUNT}원 이상이어야 합니다.",
                "/api/users/$userId/balance/charge"
            )
        }

        if (amount > MAX_CHARGE_AMOUNT) {
            return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_AMOUNT",
                "충전 금액은 ${MAX_CHARGE_AMOUNT}원 이하여야 합니다.",
                "/api/users/$userId/balance/charge"
            )
        }

        // 현재 잔액
        val previousBalance = user.balance
        val newBalance = previousBalance + amount

        // 최대 잔액 한도 체크
        if (newBalance > MAX_BALANCE) {
            return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "BALANCE_LIMIT_EXCEEDED",
                "충전 후 잔액이 최대 한도(${MAX_BALANCE}원)를 초과할 수 없습니다.",
                "/api/users/$userId/balance/charge"
            )
        }

        // 잔액 업데이트
        val chargedAt = LocalDateTime.now().format(DATE_FORMATTER)
        user.balance = newBalance
        user.updatedAt = chargedAt

        val response = ChargeBalanceResponse(
            userId = userId,
            previousBalance = previousBalance,
            chargedAmount = amount,
            currentBalance = newBalance,
            chargedAt = chargedAt
        )

        return ResponseEntity.ok(response)
    }

    // 사용자 정보 조회
    @GetMapping("/{userId}")
    fun getUserInfo(@PathVariable userId: Long): ResponseEntity<UserInfoResponse> {
        val user = USERS[userId]
            ?: return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.",
                "/api/users/$userId"
            )

        val response = UserInfoResponse(
            userId = user.userId,
            email = user.email,
            name = user.name,
            balance = user.balance,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )

        return ResponseEntity.ok(response)
    }

    private fun <T> createErrorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        path: String
    ): ResponseEntity<T> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().format(DATE_FORMATTER),
            status = status.value(),
            error = status.reasonPhrase,
            code = code,
            message = message,
            path = path
        )

        @Suppress("UNCHECKED_CAST")
        return ResponseEntity.status(status).body(errorResponse as T)
    }
}