package com.hhplus.ecommerce.domains.user

import com.hhplus.ecommerce.domains.user.dto.*
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {

    @Operation(summary = "사용자 생성", description = "새로운 사용자를 생성합니다")
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<User> {
        val user = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    @Operation(summary = "사용자 잔액 조회", description = "사용자 ID로 잔액 정보를 조회합니다")
    @GetMapping("/{userId}/balance")
    fun getUserBalance(@PathVariable userId: Long): ResponseEntity<UserBalanceResponse> {
        val response = userService.getUserBalance(userId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다")
    @PostMapping("/{userId}/balance/charge")
    fun chargeBalance(
        @PathVariable userId: Long,
        @RequestBody request: ChargeBalanceRequest
    ): ResponseEntity<ChargeBalanceResponse> {
        val response = userService.chargeBalance(userId, request.balance)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "사용자 정보 조회", description = "사용자 ID로 사용자의 상세 정보를 조회합니다")
    @GetMapping("/{userId}")
    fun getUserInfo(@PathVariable userId: Long): ResponseEntity<UserInfoResponse> {
        val response = userService.getUserInfo(userId)
        return ResponseEntity.ok(response)
    }
}