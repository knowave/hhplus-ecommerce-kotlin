package com.hhplus.ecommerce.domain.product.entity

import com.hhplus.ecommerce.common.exception.InvalidProductRankingPeriodException

enum class RankingPeriod(
    val description: String
) {
    DAILY("DAILY_RANKING"),
    WEEKLY("WEEKLY_RANKING");

    companion object {
        fun fromString(value: String): RankingPeriod {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw InvalidProductRankingPeriodException(value)
        }
    }
}