package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDateTime
import java.util.UUID

@Configuration
class CouponDataInitialize {

    @Bean
    fun couponDataInitializer(couponRepository: CouponRepository) = CommandLineRunner {
        if (couponRepository.count() == 0L) {
            val now = LocalDateTime.now()

            couponRepository.saveAll(
                listOf(
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "신규 회원 가입 축하 쿠폰",
                        discountRate = 10,
                        totalQuantity = 1000,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(30)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "첫 구매 감사 쿠폰",
                        discountRate = 15,
                        totalQuantity = 500,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(60)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "VIP 회원 전용 쿠폰",
                        discountRate = 30,
                        totalQuantity = 100,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(90)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "주말 특별 할인 쿠폰",
                        discountRate = 20,
                        totalQuantity = 300,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(7)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "전자기기 카테고리 쿠폰",
                        discountRate = 25,
                        totalQuantity = 200,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(45)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "5만원 이상 구매 쿠폰",
                        discountRate = 5,
                        totalQuantity = 800,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(30)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "10만원 이상 구매 쿠폰",
                        discountRate = 10,
                        totalQuantity = 600,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(30)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "봄맞이 시즌 할인 쿠폰",
                        discountRate = 15,
                        totalQuantity = 400,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(20)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "특가 상품 추가 할인 쿠폰",
                        discountRate = 5,
                        totalQuantity = 1500,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(14)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "생일 축하 쿠폰",
                        discountRate = 20,
                        totalQuantity = 250,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(365)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "의류 카테고리 쿠폰",
                        discountRate = 18,
                        totalQuantity = 350,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(40)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "가전제품 특별 쿠폰",
                        discountRate = 22,
                        totalQuantity = 180,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(50)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "연말 감사 대축제 쿠폰",
                        discountRate = 35,
                        totalQuantity = 150,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(10)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "친구 초대 이벤트 쿠폰",
                        discountRate = 12,
                        totalQuantity = 700,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(60)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "앱 전용 특별 쿠폰",
                        discountRate = 8,
                        totalQuantity = 900,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(30)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "리뷰 작성 감사 쿠폰",
                        discountRate = 7,
                        totalQuantity = 1200,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(90)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "재구매 고객 감사 쿠폰",
                        discountRate = 13,
                        totalQuantity = 450,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(45)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "플래시 세일 쿠폰",
                        discountRate = 50,
                        totalQuantity = 50,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusHours(24)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "50만원 이상 구매 쿠폰",
                        discountRate = 18,
                        totalQuantity = 120,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(60)
                    ),
                    Coupon(
                        id = UUID.randomUUID().toString(),
                        name = "브랜드데이 기념 쿠폰",
                        discountRate = 28,
                        totalQuantity = 300,
                        issuedQuantity = 0,
                        startDate = now,
                        endDate = now.plusDays(15)
                    )
                )
            )
        }
    }
}