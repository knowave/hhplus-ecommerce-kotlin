package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import com.hhplus.ecommerce.common.exception.AlreadyUsedCouponException
import com.hhplus.ecommerce.common.exception.ExpiredCouponException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.domain.user.entity.User
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    val coupon: Coupon,

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
        if (status != CouponStatus.AVAILABLE) {
            throw InvalidCouponException("사용 가능한 쿠폰만 사용할 수 있습니다.")
        }
        if (isExpired()) {
            throw ExpiredCouponException(coupon.id)
        }
        status = CouponStatus.USED
        usedAt = LocalDateTime.now()
    }

    fun restore() {
        if (status != CouponStatus.USED) {
            throw InvalidCouponException("사용된 쿠폰만 복원할 수 있습니다.")
        }
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