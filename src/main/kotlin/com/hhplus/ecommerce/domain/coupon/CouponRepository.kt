package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.*

@Repository
interface CouponRepository : JpaRepository<Coupon, String> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    fun findByIdWithOptimisticLock(id: String): Optional<Coupon>

    fun findByStartDateBeforeAndEndDateAfter(
        currentTime1: LocalDateTime,
        currentTime2: LocalDateTime
    ): List<Coupon>
}
