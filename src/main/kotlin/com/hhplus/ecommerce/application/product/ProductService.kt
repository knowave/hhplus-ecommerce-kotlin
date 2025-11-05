package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.*
import com.hhplus.ecommerce.domain.product.entity.Product

interface ProductService {

    fun getProducts(category: String?, sort: String?, page: Int, size: Int): ProductListResult

    fun getTopProducts(days: Int, limit: Int): TopProductsResult

    fun findProductById(id: Long): Product

    fun updateProduct(product: Product): Product
}