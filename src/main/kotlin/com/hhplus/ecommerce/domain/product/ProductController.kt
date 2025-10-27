package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.domain.product.dto.ProductResponseDto
import com.hhplus.ecommerce.domain.product.dto.TopProductsResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService
) {
    @GetMapping
    fun getAllProducts(
        @RequestParam(required = false) category: String?
    ): ResponseEntity<List<ProductResponseDto>> {
        return ResponseEntity.status(HttpStatus.OK)
            .body(productService.getAllProducts(category))
    }

    @GetMapping("/top")
    fun getTopProducts(): ResponseEntity<TopProductsResponseDto> {
        return ResponseEntity.status(HttpStatus.OK)
            .body(productService.getTopProducts())
    }
}