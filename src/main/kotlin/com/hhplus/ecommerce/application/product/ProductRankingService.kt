package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.ProductRankingListResult
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import java.time.LocalDate
import java.util.UUID

interface ProductRankingService {
    fun incrementOrderCount(
        productId: UUID,
        quantity: Int,
        period: RankingPeriod,
        date: LocalDate? = null
    )

    fun getRanking(
        period: RankingPeriod,
        date: LocalDate? = null,
        limit: Int = 10
    ): ProductRankingListResult

    fun cleanupExpiredRankings(beforeDate: LocalDate)
}