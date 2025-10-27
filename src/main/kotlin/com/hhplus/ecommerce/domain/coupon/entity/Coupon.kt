package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import com.hhplus.ecommerce.common.exception.InvalidCouponDateException
import com.hhplus.ecommerce.common.exception.InvalidCouponQuantityException
import com.hhplus.ecommerce.common.exception.InvalidDiscountRateException
import com.hhplus.ecommerce.domain.order.entity.Order
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "coupons",
    indexes = [
        Index(name = "idx_dates", columnList = "start_date, end_date")
    ]
)
class Coupon(
    id: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "discount_rate", nullable = false)
    var discountRate: Int,

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int,

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Int = 0,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDateTime,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDateTime,

    @OneToMany(mappedBy = "coupon", cascade = [CascadeType.ALL], orphanRemoval = true)
    val userCoupons: MutableList<UserCoupon> = mutableListOf(),

    @Version
    @Column(name = "version")
    var version: Long = 0
) : CustomBaseEntity(id) {

    init {
        if (discountRate !in 1..100) {
            throw InvalidDiscountRateException(discountRate)
        }
        if (totalQuantity <= 0) {
            throw InvalidCouponQuantityException("Total quantity must be greater than 0")
        }
        if (issuedQuantity < 0) {
            throw InvalidCouponQuantityException("Issued quantity must be greater than or equal to 0")
        }
        if (!startDate.isBefore(endDate)) {
            throw InvalidCouponDateException("Start date must be before end date")
        }
    }

    fun canIssue(): Boolean {
        return issuedQuantity < totalQuantity
    }

    fun issue() {
        if (!canIssue()) {
            throw CouponSoldOutException(id)
        }
        issuedQuantity++
    }

    fun isValid(currentTime: LocalDateTime = LocalDateTime.now()): Boolean {
        return currentTime.isAfter(startDate) && currentTime.isBefore(endDate)
    }
}
