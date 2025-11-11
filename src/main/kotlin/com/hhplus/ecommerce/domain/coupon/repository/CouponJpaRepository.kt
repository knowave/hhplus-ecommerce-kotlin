package com.hhplus.ecommerce.domain.coupon.repository

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

interface CouponJpaRepository : JpaRepository<Coupon, UUID> {
    @Query("""
        SELECT c FROM Coupon c
        WHERE c.startDate <= :now
        AND c.endDate >= :now
        AND c.issuedQuantity < c.totalQuantity
    """)
    fun findAvailableCoupons(@Param("now") now: LocalDateTime): List<Coupon>

    /**
     * 비관적 락을 사용하여 쿠폰을 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용합니다:
     * - 쿠폰 발급 시 issuedQuantity 증가 (선착순 처리)
     * - 트랜잭션 종료 시까지 다른 트랜잭션이 해당 행을 수정할 수 없음
     *
     * @param id 쿠폰 ID
     * @return 잠금이 걸린 쿠폰 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    fun findByIdWithLock(@Param("id") id: UUID): Optional<Coupon>
}