package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.presentation.product.dto.*
import com.hhplus.ecommerce.domain.product.entity.Product
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository
) : ProductService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun getProducts(
        category: String?,
        sort: String?,
        page: Int,
        size: Int
    ): ProductListResponse {
        // 1. 모든 상품 조회 또는 카테고리 필터링
        var products: List<Product> = if (category != null) {
            val productCategory = try {
                ProductCategory.valueOf(category.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
            if (productCategory != null) {
                productRepository.findByCategory(productCategory)
            } else {
                emptyList()
            }
        } else {
            productRepository.findAll()
        }

        // 2. 정렬
        products = when (sort) {
            "price" -> products.sortedBy { it.price }
            "popularity" -> products.sortedByDescending { it.salesCount }
            "newest", null -> products.sortedByDescending { it.createdAt }
            else -> products.sortedByDescending { it.createdAt }
        }

        // 3. 페이지네이션 계산
        val totalElements = products.size
        val totalPages = ceil(totalElements.toDouble() / size).toInt()
        val start = page * size
        val end = minOf(start + size, totalElements)

        val pagedProducts = if (start < totalElements) {
            products.subList(start, end)
        } else {
            emptyList()
        }

        // 4. DTO 변환
        val productSummaries = pagedProducts.map { product ->
            ProductSummary(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                stock = product.stock,
                salesCount = product.salesCount,
                category = product.category.name,
                createdAt = product.createdAt,
                updatedAt = product.updatedAt
            )
        }

        val pagination = Pagination(
            currentPage = page,
            totalPages = totalPages,
            totalElements = totalElements,
            size = size,
            hasNext = page < totalPages - 1,
            hasPrevious = page > 0
        )

        return ProductListResponse(
            products = productSummaries,
            pagination = pagination
        )
    }

    override fun getProductDetail(productId: Long): ProductDetailResponse {
        val product = findProductById(productId)

        return ProductDetailResponse(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            stock = product.stock,
            category = product.category.name,
            specifications = product.specifications,
            salesCount = product.salesCount,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt
        )
    }

    override fun getProductStock(productId: Long): ProductStockResponse {
        val product = findProductById(productId)

        return ProductStockResponse(
            id = product.id,
            productName = product.name,
            stock = product.stock,
            isAvailable = product.stock > 0,
            lastUpdatedAt = product.updatedAt
        )
    }

    override fun getTopProducts(days: Int, limit: Int): TopProductsResponse {
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
            TopProductItem(
                rank = index + 1,
                id = product.id,
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

        val period = Period(
            days = days,
            startDate = startDate.format(DATE_FORMATTER),
            endDate = endDate.format(DATE_FORMATTER)
        )

        return TopProductsResponse(
            period = period,
            products = topProductItems
        )
    }

    override fun findProductById(id: Long): Product {
        return productRepository.findById(id)
            ?: throw ProductNotFoundException(id)
    }

    override fun updateProduct(product: Product): Product {
        return productRepository.save(product)
    }
}