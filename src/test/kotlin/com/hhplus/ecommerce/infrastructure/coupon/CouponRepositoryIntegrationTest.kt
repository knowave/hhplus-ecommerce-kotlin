package com.hhplus.ecommerce.infrastructure.coupon

import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
class CouponRepositoryIntegrationTest(
    private val couponRepository: CouponJpaRepository
) : DescribeSpec({
}) {

    override fun extensions(): List<Extension> = listOf(SpringExtension)

    init {

        afterEach {
            couponRepository.deleteAll()
        }

        describe("CouponRepository 통합 테스트 - Coupon 관리") {
            context("findAvailableCoupons") {
                it("현재 날짜를 기준으로 사용 가능한 쿠폰을 조회한다.") {
                    // given
                    couponRepository.saveAll(
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

                    val now = LocalDateTime.now()

                    // when
                    val availableCoupons = couponRepository.findAvailableCoupons(now)

                    // then
                    availableCoupons.size shouldBe 2
                    availableCoupons.map { it.name } shouldContainExactlyInAnyOrder listOf(
                        "신규 가입 환영 쿠폰",
                        "VIP 고객 감사 쿠폰"
                    )
                }

                it("시작 전 쿠폰만 있으면 빈 리스트를 반환한다") {
                    // given
                    val now = LocalDateTime.now()
                    couponRepository.save(
                        Coupon(
                            name = "아직 시작 안한 쿠폰",
                            description = "",
                            discountRate = 5,
                            totalQuantity = 10,
                            issuedQuantity = 0,
                            startDate = now.plusDays(2),
                            endDate = now.plusDays(10),
                            validityDays = 5,
                        )
                    )

                    // when
                    val result = couponRepository.findAvailableCoupons(now)

                    // then
                    result shouldBe emptyList()
                }
            }
        }
    }
}
