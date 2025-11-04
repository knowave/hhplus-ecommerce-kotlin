package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.presentation.user.dto.*
import com.hhplus.ecommerce.domain.user.entity.User

interface UserService {

    // 사용자 잔액을 조회
    fun getUserBalance(userId: Long): UserBalanceResponse

    // 사용자 잔액 충전
    fun chargeBalance(userId: Long, amount: Long): ChargeBalanceResponse

    // 사용자 정보 조회
    fun getUserInfo(userId: Long): UserInfoResponse

    // 사용자 생성
    fun createUser(dto: CreateUserRequest): User

    fun getUser(id: Long): User

    fun updateUser(user: User): User
}