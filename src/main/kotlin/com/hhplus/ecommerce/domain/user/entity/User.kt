package com.hhplus.ecommerce.domain.user.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "users")
class User(
    id: String,

    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO
) : CustomBaseEntity(id) {

    fun chargeBalance(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "충전 금액은 0보다 커야 합니다." }
        balance = balance.add(amount)
    }

    fun deductBalance(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "차감 금액은 0보다 커야 합니다." }
        require(balance >= amount) { "잔액이 부족합니다." }
        balance = balance.subtract(amount)
    }
}