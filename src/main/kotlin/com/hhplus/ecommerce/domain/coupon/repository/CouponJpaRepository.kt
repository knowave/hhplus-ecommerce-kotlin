package com.hhplus.ecommerce.domain.coupon.repository

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface CouponJpaRepository : JpaRepository<Coupon, UUID> {
    @Query("""
        SELECT c FROM coupon c
        WHERE c.startDate <= :now
        AND c.endDate >= :now
        AND c.issuedQuantity < c.totalQuantity
    """)
    fun findAvailableCoupons(@Param("now") now: LocalDateTime = LocalDateTime.now()): List<Coupon>
}