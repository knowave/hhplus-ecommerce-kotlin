package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.ProductRanking
import com.hhplus.ecommerce.application.product.dto.ProductRankingListResult
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.UUID

@Service
class ProductRankingServiceImpl(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRepository: ProductJpaRepository
) : ProductRankingService {

    companion object {
        private val logger = LoggerFactory.getLogger(ProductRankingServiceImpl::class.java)

        private const val DAILY_RETENTION_DAYS = 30
        private const val WEEKLY_RETENTION_WEEKS = 12
        private const val DAILY_RANKING_KEY_PREFIX = "product:ranking:daily"
        private const val WEEKLY_RANKING_KEY_PREFIX = "product:ranking:weekly"
    }

    private val zSetOps: ZSetOperations<String, String> = redisTemplate.opsForZSet()

    override fun incrementOrderCount(productId: UUID, quantity: Int, period: RankingPeriod, date: LocalDate?) {
        val targetDate = date ?: LocalDate.now()
        val key = generateRankingKey(period, targetDate)

        try {
            zSetOps.incrementScore(key, productId.toString(), quantity.toDouble())

            // TTL
            val retentionDays = when (period) {
                RankingPeriod.DAILY -> DAILY_RETENTION_DAYS
                RankingPeriod.WEEKLY -> WEEKLY_RETENTION_WEEKS * 7
            }

            redisTemplate.expire(key, java.time.Duration.ofDays(retentionDays.toLong()))
            logger.debug("Incremented ranking for product {} in {} by {}", productId, key, quantity)
        } catch (e: IllegalArgumentException) {
            logger.error("Failed to increment ranking for product $productId in $key", e)
        }
    }

    override fun getRanking(
        period: RankingPeriod,
        date: LocalDate?,
        limit: Int
    ): ProductRankingListResult {
        val targetDate = date ?: LocalDate.now()
        val key = generateRankingKey(period, targetDate)

        // Redis에서 상위 limit개 조회 (내림차순)
        val rankingData = zSetOps.reverseRangeWithScores(key, 0, (limit - 1).toLong())
            ?: emptySet()

        if (rankingData.isEmpty()) {
            return ProductRankingListResult(
                period = period.name,
                date = formatDate(period, targetDate),
                rankings = emptyList(),
                totalCount = 0
            )
        }

        // productId 목록 추출
        val productIds = rankingData.mapNotNull {
            try {
                UUID.fromString(it.value)
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid product ID in ranking: ${it.value}")
                null
            }
        }

        // DB에서 상품 정보 Batch 조회
        val products = productRepository.findAllById(productIds)
            .associateBy { it.id!! }

        // DTO 변환
        val rankings = rankingData.mapIndexedNotNull { index, typedTuple ->
            try {
                val productId = UUID.fromString(typedTuple.value)
                val product = products[productId]

                if (product == null) {
                    logger.warn("Product not found for ranking: $productId")
                    return@mapIndexedNotNull null
                }

                ProductRanking(
                    rank = index + 1,
                    productId = productId,
                    productName = product.name,
                    orderCount = typedTuple.score?.toLong() ?: 0L,
                    category = product.category.name,
                    price = product.price,
                    salesCount = product.salesCount
                )
            } catch (e: Exception) {
                logger.error("Error processing ranking data: ${typedTuple.value}", e)
                null
            }
        }

        return ProductRankingListResult(
            period = period.name,
            date = formatDate(period, targetDate),
            rankings = rankings,
            totalCount = rankings.size
        )
    }

    override fun cleanupExpiredRankings(beforeDate: LocalDate) {
        try {
            var deletedCount = 0
            var currentDate = beforeDate

            repeat(DAILY_RETENTION_DAYS) {
                val key = generateRankingKey(RankingPeriod.DAILY, currentDate)
                if (redisTemplate.delete(key)) deletedCount++
                currentDate = currentDate.minusDays(1)
            }

            currentDate = beforeDate
            repeat(WEEKLY_RETENTION_WEEKS) {
                val key = generateRankingKey(RankingPeriod.WEEKLY, currentDate)
                if (redisTemplate.delete(key)) deletedCount++
                currentDate = currentDate.minusWeeks(1)
            }

            logger.info("Cleaned up {} expired ranking keys before {}", deletedCount, beforeDate)
        } catch (e: Exception) {
            logger.error("Failed to cleanup expired rankings", e)
        }
    }

    private fun generateRankingKey(period: RankingPeriod, date: LocalDate): String {
        return when (period) {
            RankingPeriod.DAILY -> {
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                "$DAILY_RANKING_KEY_PREFIX:$dateStr"
            }
            RankingPeriod.WEEKLY -> {
                val weekFields = WeekFields.ISO
                val year = date.get(weekFields.weekBasedYear())
                val week = date.get(weekFields.weekOfWeekBasedYear())
                "$WEEKLY_RANKING_KEY_PREFIX:$year-W${week.toString().padStart(2, '0')}"
            }
        }
    }

    private fun formatDate(period: RankingPeriod, date: LocalDate): String {
        return when (period) {
            RankingPeriod.DAILY -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            RankingPeriod.WEEKLY -> {
                val weekFields = WeekFields.ISO
                val year = date.get(weekFields.weekBasedYear())
                val week = date.get(weekFields.weekOfWeekBasedYear())
                "$year-W${week.toString().padStart(2, '0')}"
            }
        }
    }
}