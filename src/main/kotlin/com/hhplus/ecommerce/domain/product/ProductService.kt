package com.hhplus.ecommerce.domain.product

import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.order.OrderItemRepository
import com.hhplus.ecommerce.domain.product.dto.ProductResponseDto
import com.hhplus.ecommerce.domain.product.dto.ProductStockResponseDto
import com.hhplus.ecommerce.domain.product.dto.TopProductResponseDto
import com.hhplus.ecommerce.domain.product.dto.TopProductsResponseDto
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val orderItemRepository: OrderItemRepository
) {

    fun getAllProducts(category: String?): List<ProductResponseDto> {
        val products = if (category != null) {
            productRepository.findByCategory(category)
        } else {
            productRepository.findAll()
        }

        return products.map { product ->
            ProductResponseDto(
                productId = product.id,
                name = product.name,
                price = product.price,
                stock = product.stock,
                category = product.category
            )
        }
    }

    fun getTopProducts(): TopProductsResponseDto {
        val threeDaysAgo = LocalDateTime.now().minusDays(3)

        // 최근 3일간의 판매 데이터를 기반으로 인기 상품 조회
        val topProducts = productRepository.findTopProductsByRecentSales(threeDaysAgo)
            .take(5)

        val topProductDtoList = topProducts.mapIndexed { index, item ->
            TopProductResponseDto(
                rank = index + 1,
                productId = item.productId,
                name = item.productName,
                salesCount = item.salesCount,
                revenue = item.revenue
            )
        }

        return TopProductsResponseDto(
            period = "3days",
            products = topProductDtoList
        )
    }

    fun getProductStock(productId: String): ProductStockResponseDto {
        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException(productId) }

        return ProductStockResponseDto(
            productId = product.id,
            name = product.name,
            stock = product.stock,
            isAvailable = product.stock > 0
        )
    }
}