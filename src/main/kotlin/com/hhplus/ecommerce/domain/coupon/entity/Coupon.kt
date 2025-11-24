package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "coupon")
class Coupon(
    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val description: String,

    @Column(nullable = false)
    val discountRate: Int,

    @Column(nullable = false)
    val totalQuantity: Int,

    @Column(nullable = false)
    var issuedQuantity: Int,

    @Column(nullable = false)
    val startDate: LocalDateTime,

    @Column(nullable = false)
    val endDate: LocalDateTime,

    @Column(nullable = false)
    val validityDays: Int
) : BaseEntity() {
    /**
     * 발급 가능 상태 검증
     */
    fun validateIssuable(couponId: UUID) {
        if (this.issuedQuantity >= this.totalQuantity) {
            throw CouponSoldOutException(couponId)
        }
    }
}