package com.hhplus.ecommerce.domain.user

import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository
import com.hhplus.ecommerce.domain.coupon.dto.UserCouponResponseDto
import com.hhplus.ecommerce.domain.user.dto.CreateUserRequestDto
import com.hhplus.ecommerce.domain.user.dto.UserResponseDto
import com.hhplus.ecommerce.domain.user.entity.User
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userCouponRepository: UserCouponRepository
) {
    fun createUser(dto: CreateUserRequestDto): UserResponseDto {
        val user = userRepository.save<User>(User(
            id = UUID.randomUUID().toString(),
            balance = dto.balance
        ))

        return UserResponseDto(
            id = user.id,
            balance = user.balance
        )
    }

    fun getUser(userId: String): UserResponseDto {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        return UserResponseDto(
            id = user.id,
            balance = user.balance
        )
    }

    fun getManyUserCoupon(userId: String): List<UserCouponResponseDto> {
        return userCouponRepository.findAllCouponsForUser(userId)
    }
}