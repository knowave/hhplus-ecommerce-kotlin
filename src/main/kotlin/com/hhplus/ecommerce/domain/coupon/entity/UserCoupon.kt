package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    indexes = [
        Index(name = "idx_user_status", columnList = "user_id, status"),
        Index(name = "idx_coupon_id", columnList = "coupon_id"),
        Index(name = "idx_expires_at", columnList = "expires_at")
    ]
)
class UserCoupon(
    id: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "coupon_id", nullable = false)
    val couponId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponStatus = CouponStatus.AVAILABLE,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime
) : CustomBaseEntity(id) {

    fun use() {
        require(status == CouponStatus.AVAILABLE) { "사용 가능한 쿠폰만 사용할 수 있습니다." }
        require(!isExpired()) { "만료된 쿠폰은 사용할 수 없습니다." }
        status = CouponStatus.USED
        usedAt = LocalDateTime.now()
    }

    fun restore() {
        require(status == CouponStatus.USED) { "사용된 쿠폰만 복원할 수 있습니다." }
        if (isExpired()) {
            status = CouponStatus.EXPIRED
        } else {
            status = CouponStatus.AVAILABLE
            usedAt = null
        }
    }

    fun isExpired(currentTime: LocalDateTime = LocalDateTime.now()): Boolean {
        return currentTime.isAfter(expiresAt)
    }

    fun checkExpired(currentTime: LocalDateTime = LocalDateTime.now()) {
        if (status == CouponStatus.AVAILABLE && isExpired(currentTime)) {
            status = CouponStatus.EXPIRED
        }
    }
}
