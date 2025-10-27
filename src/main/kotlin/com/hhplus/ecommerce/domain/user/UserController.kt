package com.hhplus.ecommerce.domain.user

import com.hhplus.ecommerce.domain.coupon.dto.UserCouponResponseDto
import com.hhplus.ecommerce.domain.user.dto.CreateUserRequestDto
import com.hhplus.ecommerce.domain.user.dto.UserResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {
    @PostMapping()
    fun createUser(@RequestBody dto: CreateUserRequestDto): ResponseEntity<UserResponseDto> {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto))
    }

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: String): ResponseEntity<UserResponseDto> {
        return ResponseEntity.status(HttpStatus.OK).body(userService.getUser(userId))
    }

    @GetMapping("/{userId}/coupons")
    fun getManyUserCoupon(@PathVariable userId: String): ResponseEntity<List<UserCouponResponseDto>>{
        return ResponseEntity.status(HttpStatus.OK).body(userService.getManyUserCoupon(userId))
    }
}