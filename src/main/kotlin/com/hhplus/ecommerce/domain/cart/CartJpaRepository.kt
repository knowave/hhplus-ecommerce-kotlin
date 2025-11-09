package com.hhplus.ecommerce.domain.cart

import com.hhplus.ecommerce.domain.cart.entity.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CartJpaRepository : JpaRepository<CartItem, UUID> {}