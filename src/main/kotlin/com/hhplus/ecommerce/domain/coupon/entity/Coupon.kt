package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
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

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
) : CustomBaseEntity(id) {

    init {
        require(discountRate in 1..100) { "할인율은 1~100 사이여야 합니다." }
        require(totalQuantity > 0) { "총 수량은 0보다 커야 합니다." }
        require(issuedQuantity >= 0) { "발급 수량은 0 이상이어야 합니다." }
        require(startDate.isBefore(endDate)) { "시작일은 종료일보다 이전이어야 합니다." }
    }

    fun canIssue(): Boolean {
        return issuedQuantity < totalQuantity
    }

    fun issue() {
        require(canIssue()) { "쿠폰 발급 가능 수량을 초과했습니다." }
        issuedQuantity++
    }

    fun isValid(currentTime: LocalDateTime = LocalDateTime.now()): Boolean {
        return currentTime.isAfter(startDate) && currentTime.isBefore(endDate)
    }
}
