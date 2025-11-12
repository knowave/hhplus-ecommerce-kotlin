package com.hhplus.ecommerce.domain.cart.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "cart_item")
class CartItem(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,

    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val productId: UUID,

    @Column(nullable = false)
    var quantity: Int
) : BaseEntity()