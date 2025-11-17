package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.*
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ProductServiceImpl(
    private val productRepository: ProductJpaRepository
) : ProductService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun getProducts(request: GetProductsCommand): ProductListResult {
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

        return ProductListResult(
            products = productsResult,
            pagination = pagination
        )
    }

    /**
     * 인기 상품 조회 (캐싱 적용)
     *
     * 조회 빈도가 높고 실시간성이 덜 중요한 데이터이므로 3분간 캐싱합니다.
     * 캐시 키: "days:{days}:limit:{limit}"
     *
     * @param days 집계 기간 (일)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    @Cacheable(
        value = ["topProducts"],
        key = "'days:' + #days + ':limit:' + #limit",
        unless = "#result == null"
    )
    override fun getTopProducts(days: Int, limit: Int): TopProductsResult {
        // 1. 모든 상품을 판매량 기준으로 정렬
        val allProducts = productRepository.findAll()

        // 2. 판매량이 0보다 큰 상품만 필터링
        val soldProducts = allProducts.filter { it.salesCount > 0 }

        // 3. 정렬: 판매량 > 매출액 > productId
        val sortedProducts = soldProducts.sortedWith(
            compareByDescending<Product> { it.salesCount }
                .thenByDescending { it.price * it.salesCount } // revenue
                .thenBy { it.id }
        )

        // 4. Top N개만 선택
        val topProducts = sortedProducts.take(limit)

        // 5. DTO 변환
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

        // 6. 집계 기간 계산
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(days.toLong())

        val period = PeriodResult(
            days = days,
            startDate = startDate.format(DATE_FORMATTER),
            endDate = endDate.format(DATE_FORMATTER)
        )

        return TopProductsResult(
            period = period,
            products = topProductItems
        )
    }

    /**
     * 상품 ID로 조회 (캐싱 적용)
     *
     * 상품 정보는 조회 빈도가 매우 높고 변경 빈도가 낮으므로 10분간 캐싱합니다.
     * 캐시 키: 상품 ID
     *
     * @param id 상품 ID
     * @return 상품 정보
     */
    @Cacheable(value = ["products"], key = "#id")
    override fun findProductById(id: UUID): Product {
        return productRepository.findById(id)
            .orElseThrow { ProductNotFoundException(id) }
    }

    /**
     * 상품 정보 업데이트 (캐시 무효화)
     *
     * 상품 정보가 변경되면 해당 상품의 캐시를 즉시 삭제하여 일관성을 유지합니다.
     *
     * @param product 업데이트할 상품
     * @return 업데이트된 상품
     */
    @CacheEvict(value = ["products"], key = "#product.id")
    override fun updateProduct(product: Product): Product {
        return productRepository.save(product)
    }

    override fun findByIdWithLock(id: UUID): Product {
        return productRepository.findByIdWithLock(id)
            .orElseThrow { ProductNotFoundException(id) }
    }

    override fun findAllByIdWithLock(ids: List<UUID>): List<Product> {
        return productRepository.findAllByIdWithLock(ids)
    }
}