package com.hhplus.ecommerce.domain.user.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import com.hhplus.ecommerce.common.exception.InsufficientBalanceException
import com.hhplus.ecommerce.common.exception.InvalidAmountException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "user")
class User(
    @Column(nullable = false)
    var balance: Long = 0
) : BaseEntity() {
    /**
     * 잔액 충전
     * @param amount 충전할 금액
     * @throws InvalidAmountException 충전 금액이 0 이하인 경우
     */
    fun charge(amount: Long) {
        if (amount <= 0) {
            throw InvalidAmountException("충전 금액은 0보다 커야 합니다: $amount")
        }
        balance += amount
    }

    /**
     * 잔액 차감 (결제)
     * @param amount 차감할 금액
     * @throws InvalidAmountException 차감 금액이 0 이하인 경우
     * @throws InsufficientBalanceException 잔액이 부족한 경우
     */
    fun deduct(amount: Long) {
        if (amount <= 0) {
            throw InvalidAmountException("차감 금액은 0보다 커야 합니다: $amount")
        }
        if (balance < amount) {
            throw InsufficientBalanceException(
                required = amount,
                available = balance
            )
        }
        balance -= amount
    }

    /**
     * 잔액 환불 (결제 취소)
     * @param amount 환불할 금액
     * @throws InvalidAmountException 환불 금액이 0 이하인 경우
     */
    fun refund(amount: Long) {
        if (amount <= 0) {
            throw InvalidAmountException("환불 금액은 0보다 커야 합니다: $amount")
        }
        balance += amount
    }
}