package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.ProductRanking
import com.hhplus.ecommerce.application.product.dto.ProductRankingListResult
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.UUID

/**
 * DB 기반 상품 랭킹 서비스 (부하 테스트 환경)
 *
 * Redis 없이 순수 DB의 salesCount 필드를 사용하여 랭킹 관리.
 * load-test 프로파일일 때 활성화.
 *
 * 특징:
 * - Redis ZSet 대신 Product.salesCount를 직접 사용
 * - incrementOrderCount()는 Product의 salesCount를 증가시킴
 * - getRanking()은 salesCount DESC로 정렬하여 조회
 * - cleanupExpiredRankings()는 no-op (DB 기반이므로 정리 불필요)
 *
 * 부하 테스트 목적:
 * - Redis 캐시 없이 DB 직접 조회 시 성능 측정
 * - 인덱스 없이 Full Table Scan 부하 확인
 */
@Service
@Profile("load-test")
class ProductRankingServiceDbImpl(
    private val productRepository: ProductJpaRepository
) : ProductRankingService {

    companion object {
        private val logger = LoggerFactory.getLogger(ProductRankingServiceDbImpl::class.java)
    }

    /**
     * 주문 수량만큼 상품의 salesCount 증가
     *
     * 동기적으로 DB에 직접 업데이트합니다.
     * Redis 기반과 달리 period, date 파라미터는 무시됩니다.
     */
    @Transactional
    override fun incrementOrderCount(
        productId: UUID,
        quantity: Int,
        period: RankingPeriod,
        date: LocalDate?
    ) {
        try {
            val product = productRepository.findById(productId)
                .orElseThrow { IllegalArgumentException("Product not found: $productId") }

            product.increaseSalesCount(quantity)
            productRepository.save(product)

            logger.debug("Increased salesCount for product {} by {}", productId, quantity)
        } catch (e: Exception) {
            logger.error("Failed to increment salesCount for product $productId", e)
            throw e
        }
    }

    /**
     * 상품 랭킹 조회 (DB 직접 쿼리)
     *
     * Product 테이블을 salesCount DESC로 정렬하여 조회.
     * 인덱스가 없으면 Full Table Scan이 발생하여 성능 저하 예상.
     *
     * period, date 파라미터는 DB 기반에서는 무의미하므로 무시.
     */
    @Transactional(readOnly = true)
    override fun getRanking(
        period: RankingPeriod,
        date: LocalDate?,
        limit: Int
    ): ProductRankingListResult {
        val targetDate = date ?: LocalDate.now()

        try {
            // DB에서 salesCount 기준으로 정렬하여 상위 limit개 조회
            val pageable = PageRequest.of(0, limit)
            val topProducts = productRepository.findTopProducts(pageable)

            if (topProducts.isEmpty()) {
                return ProductRankingListResult(
                    period = period.name,
                    date = formatDate(period, targetDate),
                    rankings = emptyList(),
                    totalCount = 0
                )
            }

            // DTO 변환
            val rankings = topProducts.mapIndexed { index, product ->
                ProductRanking(
                    rank = index + 1,
                    productId = product.id!!,
                    productName = product.name,
                    orderCount = product.salesCount.toLong(),
                    category = product.category.name,
                    price = product.price,
                    salesCount = product.salesCount
                )
            }

            logger.debug("Retrieved {} top products from DB", rankings.size)

            return ProductRankingListResult(
                period = period.name,
                date = formatDate(period, targetDate),
                rankings = rankings,
                totalCount = rankings.size
            )
        } catch (e: Exception) {
            logger.error("Failed to get ranking from DB", e)
            throw e
        }
    }

    /**
     * 만료된 랭킹 정리 (no-op)
     *
     * DB 기반에서는 랭킹 데이터가 별도로 저장되지 않으므로 정리할 필요 없음.
     */
    override fun cleanupExpiredRankings(beforeDate: LocalDate) {
        // DB 기반이므로 별도의 정리 작업 불필요
        logger.debug("cleanupExpiredRankings called but no-op in DB-based implementation")
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