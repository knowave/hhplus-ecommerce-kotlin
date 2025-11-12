package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import com.hhplus.ecommerce.common.exception.ExpiredCouponException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "user_coupon")
class UserCoupon(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val couponId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CouponStatus,

    @Column(nullable = false)
    val issuedAt: LocalDateTime,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Column
    var usedAt: LocalDateTime? = null
) : BaseEntity() {
    /**
     * 쿠폰 사용
     * @throws InvalidCouponException 쿠폰을 사용할 수 없는 상태인 경우
     * @throws ExpiredCouponException 쿠폰이 만료된 경우
     */
    fun use() {
        if (status != CouponStatus.AVAILABLE) {
            throw InvalidCouponException("쿠폰을 사용할 수 없습니다. 상태: $status")
        }

        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw ExpiredCouponException(couponId)
        }

        status = CouponStatus.USED
        usedAt = LocalDateTime.now()
    }

    /**
     * 쿠폰 복구 (만료되지 않은 경우만)
     * @return 복구 성공 여부
     */
    fun restore(): Boolean {
        if (expiresAt.isBefore(LocalDateTime.now())) {
            // 만료된 쿠폰은 복구하지 않음
            status = CouponStatus.EXPIRED
            return false
        }

        status = CouponStatus.AVAILABLE
        usedAt = null
        return true
    }

    /**
     * 쿠폰 만료 여부 확인
     */
    fun isExpired(): Boolean {
        return expiresAt.isBefore(LocalDateTime.now())
    }
}