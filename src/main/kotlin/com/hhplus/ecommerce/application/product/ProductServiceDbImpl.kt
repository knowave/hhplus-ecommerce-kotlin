package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.*
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * DB 기반 상품 서비스 (부하 테스트 환경)
 *
 * Redis 캐시 없이 순수 DB만 사용하여 상품 관리.
 * load-test 프로파일일 때 활성화.
 *
 * 특징:
 * - Redis 캐시 비활성화 (모든 조회가 DB 직접 쿼리)
 * - @Cacheable, @CacheEvict 애노테이션 제거
 * - 동일한 비즈니스 로직 유지
 *
 * 부하 테스트 목적:
 * - 캐시 없이 DB 직접 조회 시 성능 측정
 * - DB 쿼리 최적화 효과 확인
 */
@Service
@Profile("load-test")
class ProductServiceDbImpl(
    private val productRepository: ProductJpaRepository
) : ProductService {

    companion object {
        private val logger = LoggerFactory.getLogger(ProductServiceDbImpl::class.java)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    @Transactional(readOnly = true)
    override fun getProducts(request: GetProductsCommand): ProductListResult {
        logger.debug("DB 기반 상품 목록 조회 - category: {}, sortBy: {}", request.category, request.sortBy)

        // 1. 카테고리 파싱
        val productCategory = request.category?.let {
            try {
                ProductCategory.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        // 정렬 조건
        val sort = when (request.sortBy) {
            GetProductsCommand.SortBy.PRICE -> {
                if (request.orderBy == GetProductsCommand.OrderBy.ASC) {
                    Sort.by(Sort.Direction.ASC, "price")
                } else {
                    Sort.by(Sort.Direction.DESC, "price")
                }
            }
            GetProductsCommand.SortBy.POPULARITY -> {
                if (request.orderBy == GetProductsCommand.OrderBy.ASC) {
                    Sort.by(Sort.Direction.ASC, "salesCount")
                } else {
                    Sort.by(Sort.Direction.DESC, "salesCount")
                }
            }
            GetProductsCommand.SortBy.NEWEST -> {
                if (request.orderBy == GetProductsCommand.OrderBy.ASC) {
                    Sort.by(Sort.Direction.ASC, "createdAt")
                } else {
                    Sort.by(Sort.Direction.DESC, "createdAt")
                }
            }
        }

        val pageable = PageRequest.of(request.page, request.size, sort)
        val productPage = productRepository.findAllWithFilter(productCategory, pageable)

        val productsResult = productPage.content

        val pagination = PaginationResult(
            currentPage = productPage.number,
            totalPages = productPage.totalPages,
            totalElements = productPage.totalElements.toInt(),
            size = productPage.size,
            hasNext = productPage.hasNext(),
            hasPrevious = productPage.hasPrevious()
        )

        logger.debug("상품 {} 개 조회 완료", productsResult.size)

        return ProductListResult(
            products = productsResult,
            pagination = pagination
        )
    }

    /**
     * 인기 상품 조회 (캐싱 없음 - DB 직접 조회)
     *
     * Redis 캐시 없이 매번 DB에서 직접 조회합니다.
     * 부하 테스트 시 DB 쿼리 성능을 측정할 수 있습니다.
     */
    @Transactional(readOnly = true)
    override fun getTopProducts(days: Int, limit: Int): TopProductsResult {
        logger.debug("DB 기반 인기 상품 조회 - days: {}, limit: {}", days, limit)

        // DB에서 salesCount 기준으로 정렬하여 상위 limit개 조회
        val pageable = PageRequest.of(0, limit)
        val topProducts = productRepository.findTopProducts(pageable)

        // DTO 변환
        val topProductItems = topProducts.mapIndexed { index, product ->
            TopProductItemResult(
                rank = index + 1,
                id = product.id!!,
                name = product.name,
                price = product.price,
                category = product.category.name,
                salesCount = product.salesCount,
                revenue = product.price * product.salesCount,
                stock = product.stock
            )
        }

        // 집계 기간 계산
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(days.toLong())

        val period = PeriodResult(
            days = days,
            startDate = startDate.format(DATE_FORMATTER),
            endDate = endDate.format(DATE_FORMATTER)
        )

        logger.debug("인기 상품 {} 개 조회 완료", topProductItems.size)

        return TopProductsResult(
            period = period,
            products = topProductItems
        )
    }

    /**
     * 상품 ID로 조회 (캐싱 없음 - DB 직접 조회)
     */
    @Transactional(readOnly = true)
    override fun findProductById(id: UUID): Product {
        logger.debug("DB 기반 상품 조회 - id: {}", id)
        return productRepository.findById(id)
            .orElseThrow { ProductNotFoundException(id) }
    }

    /**
     * 상품 정보 업데이트 (캐시 무효화 없음)
     */
    @Transactional
    override fun updateProduct(product: Product): Product {
        logger.debug("DB 기반 상품 업데이트 - id: {}", product.id)
        return productRepository.save(product)
    }

    @Transactional(readOnly = true)
    override fun findByIdWithLock(id: UUID): Product {
        logger.debug("DB 비관적 락으로 상품 조회 - id: {}", id)
        return productRepository.findByIdWithLock(id)
            .orElseThrow { ProductNotFoundException(id) }
    }

    @Transactional(readOnly = true)
    override fun findAllByIdWithLock(ids: List<UUID>): List<Product> {
        logger.debug("DB 비관적 락으로 상품 목록 조회 - count: {}", ids.size)
        return productRepository.findAllByIdWithLock(ids)
    }
}
