package com.hhplus.ecommerce.application.coupon.dto

import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponRequest

data class IssueCouponCommand(
    val userId: Long
) {
    companion object {
        fun command(request: IssueCouponRequest): IssueCouponCommand {
            return IssueCouponCommand(
                userId = request.userId
            )
        }
    }
}