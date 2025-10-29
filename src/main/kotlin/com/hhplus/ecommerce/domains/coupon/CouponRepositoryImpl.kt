package com.hhplus.ecommerce.domains.coupon

import com.hhplus.ecommerce.domains.coupon.dto.Coupon
import com.hhplus.ecommerce.domains.coupon.dto.UserCoupon
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class CouponRepositoryImpl : CouponRepository {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private var nextUserCouponId: Long = 1L

    private val coupons: MutableMap<Long, Coupon> = mutableMapOf(
        1L to Coupon(
            id = 1L,
            name = "신규 가입 환영 쿠폰",
            description = "신규 회원을 위한 10% 할인 쿠폰입니다.",
            discountRate = 10,
            totalQuantity = 100,
            issuedQuantity = 45,
            startDate = LocalDate.now().minusDays(7).format(DATE_FORMATTER),
            endDate = LocalDate.now().plusDays(23).format(DATE_FORMATTER),
            validityDays = 30,
            createdAt = LocalDateTime.now().minusDays(10).format(DATETIME_FORMATTER)
        ),
        2L to Coupon(
            id = 2L,
            name = "VIP 고객 감사 쿠폰",
            description = "VIP 고객님들을 위한 특별 20% 할인 쿠폰입니다.",
            discountRate = 20,
            totalQuantity = 50,
            issuedQuantity = 30,
            startDate = LocalDate.now().minusDays(3).format(DATE_FORMATTER),
            endDate = LocalDate.now().plusDays(27).format(DATE_FORMATTER),
            validityDays = 60,
            createdAt = LocalDateTime.now().minusDays(5).format(DATETIME_FORMATTER)
        ),
        3L to Coupon(
            id = 3L,
            name = "주말 특가 쿠폰",
            description = "주말 한정 15% 할인 쿠폰입니다.",
            discountRate = 15,
            totalQuantity = 200,
            issuedQuantity = 198,
            startDate = LocalDate.now().minusDays(1).format(DATE_FORMATTER),
            endDate = LocalDate.now().plusDays(2).format(DATE_FORMATTER),
            validityDays = 7,
            createdAt = LocalDateTime.now().minusDays(2).format(DATETIME_FORMATTER)
        ),
        4L to Coupon(
            id = 4L,
            name = "월말 결산 쿠폰",
            description = "월말 특별 세일 30% 할인 쿠폰입니다.",
            discountRate = 30,
            totalQuantity = 30,
            issuedQuantity = 30,
            startDate = LocalDate.now().minusDays(5).format(DATE_FORMATTER),
            endDate = LocalDate.now().minusDays(1).format(DATE_FORMATTER),
            validityDays = 14,
            createdAt = LocalDateTime.now().minusDays(7).format(DATETIME_FORMATTER)
        ),
        5L to Coupon(
            id = 5L,
            name = "첫 구매 혜택 쿠폰",
            description = "첫 구매 고객을 위한 5% 할인 쿠폰입니다.",
            discountRate = 5,
            totalQuantity = 500,
            issuedQuantity = 0,
            startDate = LocalDate.now().plusDays(1).format(DATE_FORMATTER),
            endDate = LocalDate.now().plusDays(30).format(DATE_FORMATTER),
            validityDays = 90,
            createdAt = LocalDateTime.now().format(DATETIME_FORMATTER)
        )
    )

    private val userCoupons: MutableMap<String, UserCoupon> = mutableMapOf()

    override fun findById(couponId: Long): Coupon? {
        return coupons[couponId]
    }

    override fun findAll(): List<Coupon> {
        return coupons.values.toList()
    }

    override fun findAvailableCoupons(): List<Coupon> {
        val today = LocalDate.now()
        return coupons.values.filter { coupon ->
            val startDate = LocalDate.parse(coupon.startDate, DATE_FORMATTER)
            val endDate = LocalDate.parse(coupon.endDate, DATE_FORMATTER)
            val isInPeriod = !today.isBefore(startDate) && !today.isAfter(endDate)
            val hasStock = coupon.issuedQuantity < coupon.totalQuantity

            isInPeriod && hasStock
        }
    }

    override fun save(coupon: Coupon): Coupon {
        coupons[coupon.id] = coupon
        return coupon
    }

    override fun findUserCoupon(userId: Long, couponId: Long): UserCoupon? {
        val key = "$userId-$couponId"
        return userCoupons[key]
    }

    override fun saveUserCoupon(userCoupon: UserCoupon): UserCoupon {
        val key = "${userCoupon.userId}-${userCoupon.couponId}"
        userCoupons[key] = userCoupon
        return userCoupon
    }

    override fun generateUserCouponId(): Long {
        return nextUserCouponId++
    }

    override fun findUserCouponsByUserId(userId: Long): List<UserCoupon> {
        return userCoupons.values.filter { it.userId == userId }
    }
}