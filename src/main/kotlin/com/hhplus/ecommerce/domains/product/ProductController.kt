package com.hhplus.ecommerce.domains.product

import com.hhplus.ecommerce.domains.product.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService
) {

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

    @GetMapping("/top")
    fun getTopProducts(
        @RequestParam(defaultValue = "3") days: Int,
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<TopProductsResponse> {
        val response = productService.getTopProducts(days, limit)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{productId}")
    fun getProductDetail(@PathVariable productId: Long): ResponseEntity<ProductDetailResponse> {
        val response = productService.getProductDetail(productId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{productId}/stock")
    fun getProductStock(@PathVariable productId: Long): ResponseEntity<ProductStockResponse> {
        val response = productService.getProductStock(productId)
        return ResponseEntity.ok(response)
    }
}