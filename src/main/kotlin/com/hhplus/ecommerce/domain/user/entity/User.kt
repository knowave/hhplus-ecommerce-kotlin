package com.hhplus.ecommerce.domain.user.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import com.hhplus.ecommerce.domain.order.entity.Order
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "users")
class User(
    id: String,

    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val orders: MutableList<Order> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val userCoupons: MutableList<UserCoupon> = mutableListOf()
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