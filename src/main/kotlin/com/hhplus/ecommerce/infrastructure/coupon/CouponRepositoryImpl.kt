package com.hhplus.ecommerce.infrastructure.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Repository
class CouponRepositoryImpl : CouponRepository {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val coupons: MutableMap<UUID, Coupon> = mutableMapOf()

    init {
        listOf<Coupon>(
            Coupon(
                name = "신규 가입 환영 쿠폰",
                description = "신규 회원을 위한 10% 할인 쿠폰입니다.",
                discountRate = 10,
                totalQuantity = 100,
                issuedQuantity = 45,
                startDate = LocalDateTime.now().minusDays(7),
                endDate = LocalDateTime.now().plusDays(23),
                validityDays = 30,
            ),
            Coupon(
                name = "VIP 고객 감사 쿠폰",
                description = "VIP 고객님들을 위한 특별 20% 할인 쿠폰입니다.",
                discountRate = 20,
                totalQuantity = 50,
                issuedQuantity = 30,
                startDate = LocalDateTime.now().minusDays(3),
                endDate = LocalDateTime.now().plusDays(27),
                validityDays = 60,
            ),
            Coupon(
                name = "주말 특가 쿠폰",
                description = "주말 한정 15% 할인 쿠폰입니다.",
                discountRate = 15,
                totalQuantity = 200,
                issuedQuantity = 198,
                startDate = LocalDateTime.now().minusDays(1),
                endDate = LocalDateTime.now().plusDays(2),
                validityDays = 7,
            ),
            Coupon(
                name = "월말 결산 쿠폰",
                description = "월말 특별 세일 30% 할인 쿠폰입니다.",
                discountRate = 30,
                totalQuantity = 30,
                issuedQuantity = 30,
                startDate = LocalDateTime.now().minusDays(5),
                endDate = LocalDateTime.now().minusDays(1),
                validityDays = 14,
            ),
            Coupon(
                name = "첫 구매 혜택 쿠폰",
                description = "첫 구매 고객을 위한 5% 할인 쿠폰입니다.",
                discountRate = 5,
                totalQuantity = 500,
                issuedQuantity = 0,
                startDate = LocalDateTime.now().plusDays(1),
                endDate = LocalDateTime.now().plusDays(30),
                validityDays = 90,
            )
        ).forEach { coupon ->
            assignCouponId(coupon)
            coupons[coupon.id!!] = coupon
        }
    }

    private val userCoupons: MutableMap<String, UserCoupon> = mutableMapOf()

    private fun assignCouponId(coupon: Coupon) {
        if (coupon.id == null) {
            val idField = coupon.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(coupon, UUID.randomUUID())
        }
    }

    private fun assignUserCouponId(userCoupon: UserCoupon) {
        if (userCoupon.id == null) {
            val idField = userCoupon.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(userCoupon, UUID.randomUUID())
        }
    }

    override fun findById(couponId: UUID): Coupon? {
        return coupons[couponId]
    }

    override fun findAll(): List<Coupon> {
        return coupons.values.toList()
    }

    override fun findAvailableCoupons(): List<Coupon> {
        val today = LocalDateTime.now()
        return coupons.values.filter { coupon ->
            val startDate = LocalDateTime.parse(coupon.startDate.toString(), DATE_FORMATTER)
            val endDate = LocalDateTime.parse(coupon.endDate.toString(), DATE_FORMATTER)
            val isInPeriod = !today.isBefore(startDate) && !today.isAfter(endDate)
            val hasStock = coupon.issuedQuantity < coupon.totalQuantity

            isInPeriod && hasStock
        }
    }

    override fun save(coupon: Coupon): Coupon {
        assignCouponId(coupon)
        coupons[coupon.id!!] = coupon
        return coupon
    }

    override fun findUserCoupon(userId: UUID, couponId: UUID): UserCoupon? {
        val key = "$userId-$couponId"
        return userCoupons[key]
    }

    override fun saveUserCoupon(userCoupon: UserCoupon): UserCoupon {
        val key = "${userCoupon.userId}-${userCoupon.couponId}"
        userCoupons[key] = userCoupon
        return userCoupon
    }

    override fun findUserCouponsByUserId(userId: UUID): List<UserCoupon> {
        return userCoupons.values.filter { it.userId == userId }
    }

    override fun findUserCouponByIdAndUserId(id: UUID, userId: UUID): UserCoupon? {
        return userCoupons.values.firstOrNull { it.id == id && it.userId == userId }
    }
}