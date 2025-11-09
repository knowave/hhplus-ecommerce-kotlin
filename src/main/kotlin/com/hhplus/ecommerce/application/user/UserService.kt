package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.application.user.dto.*
import com.hhplus.ecommerce.domain.user.entity.User

interface UserService {
    // 사용자 잔액 충전
    fun chargeBalance(userId: Long, amount: Long): ChargeBalanceResult

    // 사용자 생성
    fun createUser(dto: CreateUserCommand): User

    fun getUser(id: Long): User

    fun updateUser(user: User): User
}