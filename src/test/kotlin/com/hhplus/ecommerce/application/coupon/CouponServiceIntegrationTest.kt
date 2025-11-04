package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.UserCouponNotFoundException
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepositoryImpl
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldBeEmpty

class CouponServiceIntegrationTest : DescribeSpec({
    lateinit var couponRepository: CouponRepository
    lateinit var couponService: CouponService

    beforeEach {
        // 실제 구현체 사용
        couponRepository = CouponRepositoryImpl()
        couponService = CouponServiceImpl(couponRepository)
    }

    describe("CouponService 통합 테스트 - Service와 Repository 통합") {

        context("사용 가능한 쿠폰 목록 조회") {
            it("발급 가능한 쿠폰 목록을 조회할 수 있다") {
                // when
                val result = couponService.getAvailableCoupons()

                // then
                result.coupons.shouldNotBeEmpty()
                result.coupons.forEach { coupon ->
                    coupon.id shouldNotBe null
                    coupon.couponName shouldNotBe null
                    coupon.description shouldNotBe null
                    (coupon.discountRate > 0) shouldBe true
                    (coupon.remainingQuantity >= 0) shouldBe true
                    (coupon.totalQuantity > 0) shouldBe true
                }
            }

            it("조회된 쿠폰에는 발급 기간과 유효 기간 정보가 포함되어야 한다") {
                // when
                val result = couponService.getAvailableCoupons()

                // then
                result.coupons.first().let { coupon ->
                    coupon.issuePeriod shouldNotBe null
                    coupon.issuePeriod.startDate shouldNotBe null
                    coupon.issuePeriod.endDate shouldNotBe null
                    (coupon.validityDays > 0) shouldBe true
                }
            }
        }

        context("쿠폰 상세 조회") {
            it("쿠폰 ID로 쿠폰 상세 정보를 조회할 수 있다") {
                // given
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id

                // when
                val result = couponService.getCouponDetail(couponId)

                // then
                result.id shouldBe couponId
                result.couponName shouldNotBe null
                result.description shouldNotBe null
                (result.discountRate > 0) shouldBe true
                (result.totalQuantity > 0) shouldBe true
                (result.issuedQuantity >= 0) shouldBe true
                (result.remainingQuantity >= 0) shouldBe true
                result.issuePeriod shouldNotBe null
                (result.validityDays > 0) shouldBe true
                result.createdAt shouldNotBe null
            }

            it("쿠폰 상세 정보에는 발급 가능 여부가 포함되어야 한다") {
                // given
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id

                // when
                val result = couponService.getCouponDetail(couponId)

                // then
                result.isAvailable shouldNotBe null
            }

            it("존재하지 않는 쿠폰 조회 시 예외가 발생한다") {
                // when & then
                shouldThrow<CouponNotFoundException> {
                    couponService.getCouponDetail(999999L)
                }
            }
        }

        context("쿠폰 발급") {
            it("사용자에게 쿠폰을 발급할 수 있다") {
                // given
                val userId = 1L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val request = IssueCouponRequest(userId = userId)

                // when
                val result = couponService.issueCoupon(couponId, request)

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
                val request = IssueCouponRequest(userId = userId)
                val result = couponService.issueCoupon(couponId, request)

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
                val request = IssueCouponRequest(userId = userId)

                // 첫 번째 발급
                couponService.issueCoupon(couponId, request)

                // when & then - 두 번째 발급 시도
                shouldThrow<CouponAlreadyIssuedException> {
                    couponService.issueCoupon(couponId, request)
                }
            }

            it("존재하지 않는 쿠폰 발급 시 예외가 발생한다") {
                // given
                val userId = 4L
                val request = IssueCouponRequest(userId = userId)

                // when & then
                shouldThrow<CouponNotFoundException> {
                    couponService.issueCoupon(999999L, request)
                }
            }
        }

        context("사용자 쿠폰 목록 조회") {
            it("사용자가 발급받은 쿠폰 목록을 조회할 수 있다") {
                // given - 쿠폰 발급
                val userId = 5L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val request = IssueCouponRequest(userId = userId)
                couponService.issueCoupon(couponId, request)

                // when
                val result = couponService.getUserCoupons(userId, null)

                // then
                result.userId shouldBe userId
                result.coupons.shouldNotBeEmpty()
                result.coupons.size shouldBe 1
                result.coupons.first().let { coupon ->
                    coupon.userCouponId shouldNotBe null
                    coupon.couponId shouldBe couponId
                    coupon.couponName shouldNotBe null
                    (coupon.discountRate > 0) shouldBe true
                    coupon.status shouldBe "AVAILABLE"
                    coupon.issuedAt shouldNotBe null
                    coupon.expiresAt shouldNotBe null
                }
            }

            it("사용자 쿠폰 목록에는 요약 정보가 포함되어야 한다") {
                // given
                val userId = 6L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val request = IssueCouponRequest(userId = userId)
                couponService.issueCoupon(couponId, request)

                // when
                val result = couponService.getUserCoupons(userId, null)

                // then
                result.summary shouldNotBe null
                result.summary.totalCount shouldBe 1
                result.summary.availableCount shouldBe 1
                result.summary.usedCount shouldBe 0
                result.summary.expiredCount shouldBe 0
            }

            it("쿠폰이 없는 사용자는 빈 목록을 반환한다") {
                // given
                val userId = 7L

                // when
                val result = couponService.getUserCoupons(userId, null)

                // then
                result.userId shouldBe userId
                result.coupons.shouldBeEmpty()
                result.summary.totalCount shouldBe 0
            }

            it("상태별로 쿠폰을 필터링하여 조회할 수 있다") {
                // given
                val userId = 8L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val request = IssueCouponRequest(userId = userId)
                couponService.issueCoupon(couponId, request)

                // when - AVAILABLE 상태만 조회
                val result = couponService.getUserCoupons(userId, CouponStatus.AVAILABLE)

                // then
                result.coupons.shouldNotBeEmpty()
                result.coupons.forEach { coupon ->
                    coupon.status shouldBe "AVAILABLE"
                }
            }

            it("여러 쿠폰을 발급받은 경우 모두 조회할 수 있다") {
                // given - 두 개의 쿠폰 발급
                val userId = 9L
                val availableCoupons = couponService.getAvailableCoupons()

                // 첫 번째 쿠폰 발급
                val couponId1 = availableCoupons.coupons[0].id
                val request1 = IssueCouponRequest(userId = userId)
                couponService.issueCoupon(couponId1, request1)

                // 두 번째 쿠폰이 있는 경우 발급
                if (availableCoupons.coupons.size > 1) {
                    val couponId2 = availableCoupons.coupons[1].id
                    val request2 = IssueCouponRequest(userId = userId)
                    couponService.issueCoupon(couponId2, request2)

                    // when
                    val result = couponService.getUserCoupons(userId, null)

                    // then
                    result.coupons.size shouldBe 2
                    result.summary.totalCount shouldBe 2
                } else {
                    // 쿠폰이 1개만 있는 경우
                    val result = couponService.getUserCoupons(userId, null)
                    result.coupons.size shouldBe 1
                }
            }
        }

        context("사용자의 특정 쿠폰 조회") {
            it("사용자 ID와 사용자 쿠폰 ID로 특정 쿠폰을 조회할 수 있다") {
                // given - 쿠폰 발급
                val userId = 10L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val request = IssueCouponRequest(userId = userId)
                val issueResult = couponService.issueCoupon(couponId, request)
                val userCouponId = issueResult.userCouponId

                // when
                val result = couponService.getUserCoupon(userId, userCouponId)

                // then
                result.id shouldBe userCouponId
                result.userId shouldBe userId
                result.couponId shouldBe couponId
                result.couponName shouldNotBe null
                result.description shouldNotBe null
                (result.discountRate > 0) shouldBe true
                result.status shouldBe CouponStatus.AVAILABLE
                result.issuedAt shouldNotBe null
                result.expiresAt shouldNotBe null
                result.canUse shouldBe true
            }

            it("존재하지 않는 사용자 쿠폰 조회 시 예외가 발생한다") {
                // given
                val userId = 11L

                // when & then
                shouldThrow<UserCouponNotFoundException> {
                    couponService.getUserCoupon(userId, 999999L)
                }
            }

            it("다른 사용자의 쿠폰을 조회할 수 없다") {
                // given - 첫 번째 사용자가 쿠폰 발급
                val userId1 = 12L
                val userId2 = 13L
                val availableCoupons = couponService.getAvailableCoupons()
                val couponId = availableCoupons.coupons.first().id
                val request = IssueCouponRequest(userId = userId1)
                val issueResult = couponService.issueCoupon(couponId, request)
                val userCouponId = issueResult.userCouponId

                // when & then - 두 번째 사용자가 조회 시도
                shouldThrow<UserCouponNotFoundException> {
                    couponService.getUserCoupon(userId2, userCouponId)
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
                val request = IssueCouponRequest(userId = userId)
                val issueResult = couponService.issueCoupon(couponId, request)
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
                val request1 = IssueCouponRequest(userId = userId1)
                val result1 = couponService.issueCoupon(couponId, request1)

                // when - 두 번째 사용자 발급
                val request2 = IssueCouponRequest(userId = userId2)
                val result2 = couponService.issueCoupon(couponId, request2)

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
