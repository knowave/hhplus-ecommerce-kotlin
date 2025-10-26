package com.hhplus.ecommerce.domain.product.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_category", columnList = "category"),
        Index(name = "idx_created_at", columnList = "created_at")
    ]
)
class Product(
    id: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(name = "stock", nullable = false)
    var stock: Int,

    @Column(name = "category", nullable = false)
    var category: String,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
) : CustomBaseEntity(id) {

    fun decreaseStock(quantity: Int) {
        require(quantity > 0) { "차감 수량은 0보다 커야 합니다." }
        require(stock >= quantity) { "상품 [${name}]의 재고가 부족합니다. (요청: ${quantity}개, 재고: ${stock}개)" }
        stock -= quantity
    }

    fun increaseStock(quantity: Int) {
        require(quantity > 0) { "증가 수량은 0보다 커야 합니다." }
        stock += quantity
    }
}