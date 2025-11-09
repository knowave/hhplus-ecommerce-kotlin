package com.hhplus.ecommerce.presentation.product.dto

import com.hhplus.ecommerce.application.product.dto.PeriodResult

data class Period(
    val days: Int,
    val startDate: String,
    val endDate: String
) {
    companion object {
        fun from(result: PeriodResult): Period {
            return Period(
                days = result.days,
                startDate = result.startDate,
                endDate = result.endDate
            )
        }
    }
}