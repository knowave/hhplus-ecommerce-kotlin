package com.hhplus.ecommerce.domains.user

import com.hhplus.ecommerce.domains.user.dto.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {

    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<User> {
        val user = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    @GetMapping("/{userId}/balance")
    fun getUserBalance(@PathVariable userId: Long): ResponseEntity<UserBalanceResponse> {
        val response = userService.getUserBalance(userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{userId}/balance/charge")
    fun chargeBalance(
        @PathVariable userId: Long,
        @RequestBody request: ChargeBalanceRequest
    ): ResponseEntity<ChargeBalanceResponse> {
        val response = userService.chargeBalance(userId, request.balance)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{userId}")
    fun getUserInfo(@PathVariable userId: Long): ResponseEntity<UserInfoResponse> {
        val response = userService.getUserInfo(userId)
        return ResponseEntity.ok(response)
    }
}