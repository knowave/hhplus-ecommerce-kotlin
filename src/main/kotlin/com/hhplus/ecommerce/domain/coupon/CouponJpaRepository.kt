package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CouponJpaRepository : JpaRepository<Coupon, UUID> {
}