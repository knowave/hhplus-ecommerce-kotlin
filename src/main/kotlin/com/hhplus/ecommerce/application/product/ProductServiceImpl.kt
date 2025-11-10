package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.*
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil

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

    override fun findProductById(id: UUID): Product {
        return productRepository.findById(id)
            .orElseThrow { ProductNotFoundException(id) }
    }

    override fun updateProduct(product: Product): Product {
        return productRepository.save(product)
    }
}