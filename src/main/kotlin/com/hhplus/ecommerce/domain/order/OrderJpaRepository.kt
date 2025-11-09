package com.hhplus.ecommerce.domain.order

import com.hhplus.ecommerce.domain.order.entity.Order
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderJpaRepository : JpaRepository<Order, UUID> {
}