package com.hhplus.ecommerce.domain.coupon.entity

import com.hhplus.ecommerce.common.exception.ExpiredCouponException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class UserCoupon(
    val id: Long,
    val userId: Long,
    val couponId: Long,
    var status: CouponStatus,
    val issuedAt: String,
    val expiresAt: String,
    var usedAt: String? = null
) {
    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    /**
     * 쿠폰 사용
     * @throws InvalidCouponException 쿠폰을 사용할 수 없는 상태인 경우
     * @throws ExpiredCouponException 쿠폰이 만료된 경우
     */
    fun use() {
        if (status != CouponStatus.AVAILABLE) {
            throw InvalidCouponException("쿠폰을 사용할 수 없습니다. 상태: $status")
        }

        val expiresAtTime = LocalDateTime.parse(expiresAt, DATE_FORMATTER)
        if (expiresAtTime.isBefore(LocalDateTime.now())) {
            throw ExpiredCouponException(couponId)
        }

        status = CouponStatus.USED
        usedAt = LocalDateTime.now().format(DATE_FORMATTER)
    }

    /**
     * 쿠폰 복구 (만료되지 않은 경우만)
     * @return 복구 성공 여부
     */
    fun restore(): Boolean {
        val expiresAtTime = LocalDateTime.parse(expiresAt, DATE_FORMATTER)
        if (expiresAtTime.isBefore(LocalDateTime.now())) {
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
        val expiresAtTime = LocalDateTime.parse(expiresAt, DATE_FORMATTER)
        return expiresAtTime.isBefore(LocalDateTime.now())
    }
}