package com.hhplus.ecommerce.domain.shipping

import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShippingJpaRepository : JpaRepository<Shipping, UUID> {
}