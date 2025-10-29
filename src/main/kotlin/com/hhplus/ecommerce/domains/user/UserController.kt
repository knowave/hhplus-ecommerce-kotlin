package com.hhplus.ecommerce.domains.user

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MAX_BALANCE = 10_000_000L
private const val MIN_CHARGE_AMOUNT = 1_000L
private const val MAX_CHARGE_AMOUNT = 1_000_000L

private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
private val USERS: MutableMap<Long, MutableMap<String, Any>> = mutableMapOf(
    1L to mutableMapOf(
        "userId" to 1L,
        "email" to "user1@example.com",
        "name" to "홍길동",
        "balance" to 50000L,
        "createdAt" to "2025-01-01T00:00:00",
        "updatedAt" to "2025-10-29T10:30:00"
    ),
    2L to mutableMapOf(
        "userId" to 2L,
        "email" to "user2@example.com",
        "name" to "김철수",
        "balance" to 100000L,
        "createdAt" to "2025-01-15T00:00:00",
        "updatedAt" to "2025-10-29T09:00:00"
    ),
    3L to mutableMapOf(
        "userId" to 3L,
        "email" to "user3@example.com",
        "name" to "이영희",
        "balance" to 25000L,
        "createdAt" to "2025-02-01T00:00:00",
        "updatedAt" to "2025-10-28T15:20:00"
    )
)

@RestController
@RequestMapping("/api/users")
class UserController {

    // 사용자 잔액 조회
    @GetMapping("/{userId}/balance")
    fun getUserBalance(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
        val user = USERS[userId]
            ?: return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.",
                "/api/users/$userId/balance"
            )

        val response = mapOf(
            "userId" to user["userId"]!!,
            "balance" to user["balance"]!!,
            "currency" to "KRW",
            "lastUpdatedAt" to user["updatedAt"]!!
        )

        return ResponseEntity.ok(response)
    }

    // 사용자 잔액 충전
    @PostMapping("/{userId}/balance/charge")
    fun chargeBalance(
        @PathVariable userId: Long,
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val user = USERS[userId]
            ?: return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.",
                "/api/users/$userId/balance/charge"
            )

        // 충전 금액 추출
        val amount = when (val amt = request["amount"]) {
            is Number -> amt.toLong()
            else -> return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_AMOUNT",
                "충전 금액이 유효하지 않습니다.",
                "/api/users/$userId/balance/charge"
            )
        }

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
        val previousBalance = user["balance"] as Long
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
        user["balance"] = newBalance
        user["updatedAt"] = chargedAt

        val response = mapOf(
            "userId" to userId,
            "previousBalance" to previousBalance,
            "chargedAmount" to amount,
            "currentBalance" to newBalance,
            "chargedAt" to chargedAt
        )

        return ResponseEntity.ok(response)
    }

    // 사용자 정보 조회
    @GetMapping("/{userId}")
    fun getUserInfo(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
        val user = USERS[userId]
            ?: return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.",
                "/api/users/$userId"
            )

        val response = mapOf(
            "userId" to user["userId"]!!,
            "email" to user["email"]!!,
            "name" to user["name"]!!,
            "balance" to user["balance"]!!,
            "createdAt" to user["createdAt"]!!,
            "updatedAt" to user["updatedAt"]!!
        )

        return ResponseEntity.ok(response)
    }

    private fun createErrorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        path: String
    ): ResponseEntity<Map<String, Any>> {
        val errorResponse = mapOf(
            "timestamp" to LocalDateTime.now().format(DATE_FORMATTER),
            "status" to status.value(),
            "error" to status.reasonPhrase,
            "code" to code,
            "message" to message,
            "path" to path
        )

        return ResponseEntity.status(status).body(errorResponse)
    }
}