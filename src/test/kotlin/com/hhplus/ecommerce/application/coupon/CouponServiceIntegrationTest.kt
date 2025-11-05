package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepositoryImpl
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CouponServiceIntegrationTest : DescribeSpec({
    lateinit var couponRepository: CouponRepository
    lateinit var couponService: CouponService

    beforeEach {
        // 실제 구현체 사용
        couponRepository = CouponRepositoryImpl()
        val lockManager = com.hhplus.ecommerce.common.lock.LockManager()
        couponService = CouponServiceImpl(couponRepository, lockManager)
    }

    describe("CouponService 통합 테스트 - Service와 Repository 통합") {
        context("쿠폰 발급") {
            it("사용자에게 쿠폰을 발급할 수 있다") {
                // given
                val userId = 1L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val command = IssueCouponCommand(userId = userId)

                // when
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.userCouponId shouldNotBe null
                result.userId shouldBe userId
                result.couponId shouldBe couponId
                result.couponName shouldNotBe null
                (result.discountRate > 0) shouldBe true
                result.status shouldBe "AVAILABLE"
                result.issuedAt shouldNotBe null
                result.expiresAt shouldNotBe null
            }

            it("쿠폰 발급 후 잔여 수량이 감소한다") {
                // given
                val userId = 2L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id

                // 발급 전 잔여 수량 확인
                val beforeDetail = couponService.getCouponDetail(couponId)
                val remainingBefore = beforeDetail.remainingQuantity

                // when
                val command = IssueCouponCommand(userId = userId)
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.remainingQuantity shouldBe (remainingBefore - 1)

                // 발급 후 쿠폰 상세 정보로도 확인
                val afterDetail = couponService.getCouponDetail(couponId)
                afterDetail.remainingQuantity shouldBe (remainingBefore - 1)
                afterDetail.issuedQuantity shouldBe (beforeDetail.issuedQuantity + 1)
            }

            it("같은 사용자에게 같은 쿠폰을 중복 발급할 수 없다") {
                // given
                val userId = 3L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val command = IssueCouponCommand(userId = userId)

                // 첫 번째 발급
                couponService.issueCoupon(couponId, command)

                // when & then - 두 번째 발급 시도
                shouldThrow<CouponAlreadyIssuedException> {
                    couponService.issueCoupon(couponId, command)
                }
            }

            it("존재하지 않는 쿠폰 발급 시 예외가 발생한다") {
                // given
                val userId = 4L
                val command = IssueCouponCommand(userId = userId)

                // when & then
                shouldThrow<CouponNotFoundException> {
                    couponService.issueCoupon(999999L, command)
                }
            }
        }

        context("복합 시나리오 - 쿠폰 발급 및 조회 통합") {
            it("쿠폰 발급 후 목록 조회, 상세 조회를 연속으로 수행할 수 있다") {
                // given
                val userId = 14L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id

                // Step 1: 쿠폰 발급
                val command = IssueCouponCommand(userId = userId)
                val issueResult = couponService.issueCoupon(couponId, command)
                issueResult.status shouldBe "AVAILABLE"

                // Step 2: 사용자 쿠폰 목록 조회
                val listResult = couponService.getUserCoupons(userId, null)
                listResult.coupons.size shouldBe 1
                listResult.summary.availableCount shouldBe 1

                // Step 3: 특정 쿠폰 상세 조회
                val detailResult = couponService.getUserCoupon(userId, issueResult.userCouponId)
                detailResult.status shouldBe CouponStatus.AVAILABLE
                detailResult.canUse shouldBe true
            }

            it("여러 사용자가 동일한 쿠폰을 발급받을 수 있고, 각자 독립적으로 관리된다") {
                // given
                val userId1 = 15L
                val userId2 = 16L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id

                // when - 첫 번째 사용자 발급
                val command1 = IssueCouponCommand(userId = userId1)
                val result1 = couponService.issueCoupon(couponId, command1)

                // when - 두 번째 사용자 발급
                val command2 = IssueCouponCommand(userId = userId2)
                val result2 = couponService.issueCoupon(couponId, command2)

                // then - 각각 발급 성공
                result1.userId shouldBe userId1
                result2.userId shouldBe userId2

                // then - 각 사용자의 쿠폰 목록에서 확인
                val list1 = couponService.getUserCoupons(userId1, null)
                val list2 = couponService.getUserCoupons(userId2, null)

                list1.coupons.size shouldBe 1
                list2.coupons.size shouldBe 1
                list1.coupons.first().userCouponId shouldNotBe list2.coupons.first().userCouponId
            }
        }
    }
})
