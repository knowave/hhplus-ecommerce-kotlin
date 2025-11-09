package com.hhplus.ecommerce.infrastructure.coupon

import com.hhplus.ecommerce.domain.coupon.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDateTime

@Configuration
class CouponDataInitializer {

    @Bean
    fun couponDataInitializerRunner(couponRepository: CouponJpaRepository) = CommandLineRunner {
        if (couponRepository.count() == 0L) {
            couponRepository.saveAll<Coupon>(
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
                )
            )
        }
    }
}