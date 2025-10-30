package com.hhplus.ecommerce.domains.product

import com.hhplus.ecommerce.domains.product.dto.*
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService
) {

    @Operation(summary = "상품 목록 조회", description = "카테고리 필터링, 정렬, 페이징을 지원하는 상품 목록을 조회합니다")
    @GetMapping
    fun getProducts(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false, defaultValue = "newest") sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ProductListResponse> {
        val response = productService.getProducts(category, sort, page, size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "인기 상품 조회", description = "최근 일정 기간 동안의 판매량 기준 인기 상품을 조회합니다")
    @GetMapping("/top")
    fun getTopProducts(
        @RequestParam(defaultValue = "3") days: Int,
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<TopProductsResponse> {
        val response = productService.getTopProducts(days, limit)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품의 상세 정보를 조회합니다")
    @GetMapping("/{productId}")
    fun getProductDetail(@PathVariable productId: Long): ResponseEntity<ProductDetailResponse> {
        val response = productService.getProductDetail(productId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "상품 재고 조회", description = "상품 ID로 현재 재고 정보를 조회합니다")
    @GetMapping("/{productId}/stock")
    fun getProductStock(@PathVariable productId: Long): ResponseEntity<ProductStockResponse> {
        val response = productService.getProductStock(productId)
        return ResponseEntity.ok(response)
    }
}