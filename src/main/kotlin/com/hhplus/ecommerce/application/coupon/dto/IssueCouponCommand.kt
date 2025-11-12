package com.hhplus.ecommerce.application.coupon.dto

import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponRequest
import java.util.UUID

data class IssueCouponCommand(
    val userId: UUID
) {
    companion object {
        fun command(request: IssueCouponRequest): IssueCouponCommand {
            return IssueCouponCommand(
                userId = request.userId
            )
        }
    }
}