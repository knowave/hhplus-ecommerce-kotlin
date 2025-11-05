package com.hhplus.ecommerce.presentation.coupon.dto

import com.hhplus.ecommerce.application.coupon.dto.IssuePeriodDto

data class IssuePeriod(
    val startDate: String,
    val endDate: String
) {
    companion object {
        fun from(result: IssuePeriodDto): IssuePeriod {
            return IssuePeriod(
                startDate = result.startDate,
                endDate = result.endDate
            )
        }
    }
}