package com.hhplus.ecommerce.presentation.coupon.dto

import com.hhplus.ecommerce.application.coupon.dto.UserCouponItemDto
import com.hhplus.ecommerce.application.coupon.dto.UserCouponListResult
import com.hhplus.ecommerce.application.coupon.dto.UserCouponSummaryDto
import java.util.UUID

data class UserCouponListResponse(
    val userId: UUID,
    val coupons: List<UserCouponItem>,
    val summary: UserCouponSummary
) {
    companion object {
        fun from(result: UserCouponListResult): UserCouponListResponse {
            return UserCouponListResponse(
                userId = result.userId,
                coupons = result.coupons.map { UserCouponItem.from(it) },
                summary = UserCouponSummary.from(result.summary)
            )
        }
    }
}

data class UserCouponItem(
    val userCouponId: UUID,
    val couponId: UUID,
    val couponName: String,
    val discountRate: Int,
    val status: String,
    val issuedAt: String,
    val expiresAt: String,
    val usedAt: String?,
    val isExpired: Boolean,
    val daysRemaining: Int
) {
    companion object {
        fun from(result: UserCouponItemDto): UserCouponItem {
            return UserCouponItem(
                userCouponId = result.userCouponId,
                couponId = result.couponId,
                couponName = result.couponName,
                discountRate = result.discountRate,
                status = result.status,
                issuedAt = result.issuedAt,
                expiresAt = result.expiresAt,
                usedAt = result.usedAt,
                isExpired = result.isExpired,
                daysRemaining = result.daysRemaining
            )
        }
    }
}

data class UserCouponSummary(
    val totalCount: Int,
    val availableCount: Int,
    val usedCount: Int,
    val expiredCount: Int
) {
    companion object {
        fun from(result: UserCouponSummaryDto): UserCouponSummary {
            return UserCouponSummary(
                totalCount = result.totalCount,
                availableCount =  result.availableCount,
                usedCount = result.usedCount,
                expiredCount = result.expiredCount
            )
        }
    }
}