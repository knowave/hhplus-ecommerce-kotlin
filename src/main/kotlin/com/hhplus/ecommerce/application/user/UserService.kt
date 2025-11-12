package com.hhplus.ecommerce.application.user

import com.hhplus.ecommerce.application.user.dto.*
import com.hhplus.ecommerce.domain.user.entity.User
import java.util.UUID

interface UserService {
    // 사용자 잔액 충전
    fun chargeBalance(userId: UUID, amount: Long): ChargeBalanceResult

    // 사용자 생성
    fun createUser(dto: CreateUserCommand): User

    fun getUser(id: UUID): User

    fun updateUser(user: User): User

    /**
     * 비관적 락을 사용하여 사용자를 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용:
     * - 잔액 차감/환불 시
     */
    fun findByIdWithLock(id: UUID): User
}