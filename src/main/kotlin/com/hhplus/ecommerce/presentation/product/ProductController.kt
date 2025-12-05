package com.hhplus.ecommerce.presentation.product

import com.hhplus.ecommerce.application.product.ProductRankingService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.product.dto.GetProductsCommand
import com.hhplus.ecommerce.application.product.dto.ProductRankingListResult
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import com.hhplus.ecommerce.presentation.product.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService,
    private val productRankingService: ProductRankingService
) {

    @Operation(summary = "상품 목록 조회", description = "카테고리 필터링, 정렬, 페이징을 지원하는 상품 목록을 조회합니다")
    @GetMapping
    fun getProducts(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false, defaultValue = "newest") sortBy: String?,
        @RequestParam(required = false, defaultValue = "desc") orderBy: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ProductListResponse> {
        val request = GetProductsCommand(
            category = category,
            sortBy = GetProductsCommand.SortBy.fromString(sortBy),
            orderBy = GetProductsCommand.OrderBy.fromString(orderBy),
            page = page,
            size = size
        )
        val result = productService.getProducts(request)
        return ResponseEntity.ok(ProductListResponse.from(result))
    }

    @Operation(summary = "인기 상품 조회", description = "최근 일정 기간 동안의 판매량 기준 인기 상품을 조회합니다")
    @GetMapping("/top")
    fun getTopProducts(
        @RequestParam(defaultValue = "3") days: Int,
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<TopProductsResponse> {
        val result = productService.getTopProducts(days, limit)
        return ResponseEntity.ok(TopProductsResponse.from(result))
    }

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품의 상세 정보를 조회합니다")
    @GetMapping("/{productId}")
    fun getProductDetail(@PathVariable productId: UUID): ResponseEntity<ProductDetailResponse> {
        val product = productService.findProductById(productId)
        return ResponseEntity.ok(ProductDetailResponse.from(product))
    }

    @Operation(summary = "상품 재고 조회", description = "상품 ID로 현재 재고 정보를 조회합니다")
    @GetMapping("/{productId}/stock")
    fun getProductStock(@PathVariable productId: UUID): ResponseEntity<ProductStockResponse> {
        val product = productService.findProductById(productId)
        return ResponseEntity.ok(ProductStockResponse.from(product))
    }

    @Operation(summary = "특정 날짜의 일간 랭킹 조회", description = "특정 날짜의 상품 랭킹을 조회합니다.")
    @GetMapping("/rankings/daily/{date}")
    fun getDailyRankingByDate(
        @Parameter(description = "조회 날짜 (yyyy-MM-dd)", example = "2025-12-01")
        @PathVariable date: String,
        @Parameter(description = "조회할 랭킹 개수", example = "10")
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ProductRankingListResult> {
        val targetDate = try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }

        val result = productRankingService.getRanking(
            period = RankingPeriod.DAILY,
            date = targetDate,
            limit = limit.coerceIn(1, 100)
        )
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "특정 주의 주간 랭킹 조회", description = "특정 주의 상품 랭킹을 조회합니다.")
    @GetMapping("/rankings/weekly/{date}")
    fun getWeeklyRankingByDate(
        @Parameter(description = "해당 주의 날짜 (yyyy-MM-dd)", example = "2025-12-01")
        @PathVariable date: String,
        @Parameter(description = "조회할 랭킹 개수", example = "10")
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ProductRankingListResult> {
        val targetDate = try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }

        val result = productRankingService.getRanking(
            period = RankingPeriod.WEEKLY,
            date = targetDate,
            limit = limit.coerceIn(1, 100)
        )
        return ResponseEntity.ok(result)
    }
}