package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import com.hhplus.ecommerce.common.exception.InvalidCouponDateException
import com.hhplus.ecommerce.common.exception.UserCouponNotFoundException
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponStatus
import com.hhplus.ecommerce.model.coupon.Coupon
import com.hhplus.ecommerce.model.coupon.UserCoupon
import com.hhplus.ecommerce.presentation.coupon.dto.IssueCouponRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CouponServiceUnitTest : DescribeSpec({
    lateinit var couponRepository: CouponRepository
    lateinit var couponService: CouponServiceImpl
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    beforeEach {
        couponRepository = mockk()
        couponService = CouponServiceImpl(couponRepository)
    }

    describe("CouponService 단위 테스트 - issueCoupon") {
        context("정상 케이스") {
            it("유효한 쿠폰을 정상적으로 발급한다") {
                // given
                val couponId = 1L
                val userId = 100L
                val userCouponId = 1L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "신규 회원 할인",
                    description = "10% 할인 쿠폰",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.format(dateFormatter),
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns userCouponId
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, request)

                // then
                result.userCouponId shouldBe userCouponId
                result.userId shouldBe userId
                result.couponId shouldBe couponId
                result.couponName shouldBe "신규 회원 할인"
                result.discountRate shouldBe 10
                result.status shouldBe "AVAILABLE"
                result.issuedAt shouldNotBe null
                result.expiresAt shouldNotBe null
                result.remainingQuantity shouldBe 99 // 100 - 1
                result.totalQuantity shouldBe 100

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 1) { couponRepository.findUserCoupon(userId, couponId) }
                verify(exactly = 1) { couponRepository.save(any()) }
                verify(exactly = 1) { couponRepository.generateUserCouponId() }
                verify(exactly = 1) { couponRepository.saveUserCoupon(any()) }
            }

            it("마지막 남은 쿠폰을 발급할 수 있다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "마지막 쿠폰",
                    description = "설명",
                    discountRate = 20,
                    totalQuantity = 100,
                    issuedQuantity = 99, // 마지막 1개
                    startDate = today.format(dateFormatter),
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns 1L
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, request)

                // then
                result.remainingQuantity shouldBe 0 // 100 - 100

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 1) { couponRepository.save(any()) }
            }

            it("발급 기간 시작일에 쿠폰을 발급할 수 있다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "오늘 시작",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.format(dateFormatter), // 오늘 시작
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns 1L
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, request)

                // then
                result.couponId shouldBe couponId

                verify(exactly = 1) { couponRepository.findById(couponId) }
            }

            it("발급 기간 종료일에 쿠폰을 발급할 수 있다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "오늘 종료",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.minusDays(30).format(dateFormatter),
                    endDate = today.format(dateFormatter), // 오늘 종료
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns 1L
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, request)

                // then
                result.couponId shouldBe couponId

                verify(exactly = 1) { couponRepository.findById(couponId) }
            }
        }

        context("예외 케이스 - 쿠폰 미존재") {
            it("존재하지 않는 쿠폰 ID로 발급 시도 시 CouponNotFoundException을 발생시킨다") {
                // given
                val invalidCouponId = 999L
                val userId = 100L
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(invalidCouponId) } returns null

                // when & then
                val exception = shouldThrow<CouponNotFoundException> {
                    couponService.issueCoupon(invalidCouponId, request)
                }
                exception.message shouldContain "Coupon not found with id: $invalidCouponId"

                verify(exactly = 1) { couponRepository.findById(invalidCouponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }

        context("예외 케이스 - 중복 발급") {
            it("이미 발급받은 쿠폰을 재발급 시도 시 CouponAlreadyIssuedException을 발생시킨다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.format(dateFormatter),
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val existingUserCoupon = UserCoupon(
                    id = 1L,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-12-01 10:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns existingUserCoupon

                // when & then
                val exception = shouldThrow<CouponAlreadyIssuedException> {
                    couponService.issueCoupon(couponId, request)
                }
                exception.message shouldContain "User already has this coupon"

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 1) { couponRepository.findUserCoupon(userId, couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }

        context("예외 케이스 - 발급 기간") {
            it("발급 기간 시작 전에 발급 시도 시 InvalidCouponDateException을 발생시킨다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "미래 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.plusDays(1).format(dateFormatter), // 내일 시작
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<InvalidCouponDateException> {
                    couponService.issueCoupon(couponId, request)
                }
                exception.message shouldContain "쿠폰 발급 기간이 시작되지 않았습니다"

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }

            it("발급 기간 종료 후에 발급 시도 시 InvalidCouponDateException을 발생시킨다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "만료된 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.minusDays(30).format(dateFormatter),
                    endDate = today.minusDays(1).format(dateFormatter), // 어제 종료
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<InvalidCouponDateException> {
                    couponService.issueCoupon(couponId, request)
                }
                exception.message shouldContain "쿠폰 발급 기간이 종료되었습니다"

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }

        context("예외 케이스 - 재고 부족") {
            it("품절된 쿠폰 발급 시도 시 CouponSoldOutException을 발생시킨다") {
                // given
                val couponId = 1L
                val userId = 100L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "품절 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 100, // 이미 모두 발급됨
                    startDate = today.format(dateFormatter),
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )
                val request = IssueCouponRequest(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<CouponSoldOutException> {
                    couponService.issueCoupon(couponId, request)
                }
                exception.message shouldContain "Coupon sold out"

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }
    }

    describe("CouponService 단위 테스트 - getAvailableCoupons") {
        context("정상 케이스") {
            it("발급 가능한 쿠폰 목록을 조회한다") {
                // given
                val today = LocalDate.now()
                val coupons = listOf(
                    Coupon(1L, "쿠폰1", "설명1", 10, 100, 50, today.format(dateFormatter), today.plusDays(30).format(dateFormatter), 30, "2025-10-01 00:00:00"),
                    Coupon(2L, "쿠폰2", "설명2", 20, 200, 100, today.format(dateFormatter), today.plusDays(30).format(dateFormatter), 30, "2025-10-01 00:00:00"),
                    Coupon(3L, "쿠폰3", "설명3", 30, 300, 0, today.format(dateFormatter), today.plusDays(30).format(dateFormatter), 30, "2025-10-01 00:00:00")
                )

                every { couponRepository.findAvailableCoupons() } returns coupons

                // when
                val result = couponService.getAvailableCoupons()

                // then
                result.coupons shouldHaveSize 3
                result.coupons[0].id shouldBe 1L
                result.coupons[0].couponName shouldBe "쿠폰1"
                result.coupons[0].discountRate shouldBe 10
                result.coupons[0].remainingQuantity shouldBe 50 // 100 - 50
                result.coupons[1].remainingQuantity shouldBe 100 // 200 - 100
                result.coupons[2].remainingQuantity shouldBe 300 // 300 - 0

                verify(exactly = 1) { couponRepository.findAvailableCoupons() }
            }

            it("발급 가능한 쿠폰이 없으면 빈 목록을 반환한다") {
                // given
                every { couponRepository.findAvailableCoupons() } returns emptyList()

                // when
                val result = couponService.getAvailableCoupons()

                // then
                result.coupons.shouldBeEmpty()

                verify(exactly = 1) { couponRepository.findAvailableCoupons() }
            }
        }
    }

    describe("CouponService 단위 테스트 - getCouponDetail") {
        context("정상 케이스") {
            it("쿠폰 상세 정보를 조회한다 (발급 가능)") {
                // given
                val couponId = 1L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "상세 쿠폰",
                    description = "상세 설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 30,
                    startDate = today.format(dateFormatter),
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                every { couponRepository.findById(couponId) } returns coupon

                // when
                val result = couponService.getCouponDetail(couponId)

                // then
                result.id shouldBe couponId
                result.couponName shouldBe "상세 쿠폰"
                result.description shouldBe "상세 설명"
                result.discountRate shouldBe 10
                result.totalQuantity shouldBe 100
                result.issuedQuantity shouldBe 30
                result.remainingQuantity shouldBe 70
                result.validityDays shouldBe 30
                result.isAvailable shouldBe true // 기간 내, 재고 있음

                verify(exactly = 1) { couponRepository.findById(couponId) }
            }

            it("품절된 쿠폰은 isAvailable이 false이다") {
                // given
                val couponId = 1L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "품절 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 100, // 품절
                    startDate = today.format(dateFormatter),
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                every { couponRepository.findById(couponId) } returns coupon

                // when
                val result = couponService.getCouponDetail(couponId)

                // then
                result.isAvailable shouldBe false
                result.remainingQuantity shouldBe 0

                verify(exactly = 1) { couponRepository.findById(couponId) }
            }

            it("발급 기간이 아닌 쿠폰은 isAvailable이 false이다") {
                // given
                val couponId = 1L
                val today = LocalDate.now()
                val coupon = Coupon(
                    id = couponId,
                    name = "미래 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = today.plusDays(1).format(dateFormatter), // 내일 시작
                    endDate = today.plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                every { couponRepository.findById(couponId) } returns coupon

                // when
                val result = couponService.getCouponDetail(couponId)

                // then
                result.isAvailable shouldBe false

                verify(exactly = 1) { couponRepository.findById(couponId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 쿠폰 조회 시 CouponNotFoundException을 발생시킨다") {
                // given
                val invalidCouponId = 999L
                every { couponRepository.findById(invalidCouponId) } returns null

                // when & then
                shouldThrow<CouponNotFoundException> {
                    couponService.getCouponDetail(invalidCouponId)
                }

                verify(exactly = 1) { couponRepository.findById(invalidCouponId) }
            }
        }
    }

    describe("CouponService 단위 테스트 - getUserCoupons") {
        context("정상 케이스 - 전체 조회") {
            it("사용자의 모든 쿠폰을 조회한다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()
                val userCoupons = listOf(
                    UserCoupon(1L, userId, 1L, CouponStatus.AVAILABLE, now.format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter)),
                    UserCoupon(2L, userId, 2L, CouponStatus.USED, now.minusDays(10).format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter), now.format(dateTimeFormatter)),
                    UserCoupon(3L, userId, 3L, CouponStatus.EXPIRED, now.minusDays(40).format(dateTimeFormatter), now.minusDays(10).format(dateTimeFormatter))
                )
                val coupon1 = Coupon(1L, "쿠폰1", "설명", 10, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val coupon2 = Coupon(2L, "쿠폰2", "설명", 20, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val coupon3 = Coupon(3L, "쿠폰3", "설명", 30, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                every { couponRepository.findUserCouponsByUserId(userId) } returns userCoupons
                every { couponRepository.findById(1L) } returns coupon1
                every { couponRepository.findById(2L) } returns coupon2
                every { couponRepository.findById(3L) } returns coupon3

                // when
                val result = couponService.getUserCoupons(userId, null)

                // then
                result.userId shouldBe userId
                result.coupons shouldHaveSize 3
                result.summary.totalCount shouldBe 3
                result.summary.availableCount shouldBe 1
                result.summary.usedCount shouldBe 1
                result.summary.expiredCount shouldBe 1

                verify(exactly = 1) { couponRepository.findUserCouponsByUserId(userId) }
            }

            it("사용자가 쿠폰을 가지고 있지 않으면 빈 목록을 반환한다") {
                // given
                val userId = 100L
                every { couponRepository.findUserCouponsByUserId(userId) } returns emptyList()

                // when
                val result = couponService.getUserCoupons(userId, null)

                // then
                result.coupons.shouldBeEmpty()
                result.summary.totalCount shouldBe 0

                verify(exactly = 1) { couponRepository.findUserCouponsByUserId(userId) }
            }
        }

        context("정상 케이스 - 상태 필터링") {
            it("AVAILABLE 상태의 쿠폰만 조회한다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()
                val userCoupons = listOf(
                    UserCoupon(1L, userId, 1L, CouponStatus.AVAILABLE, now.format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter)),
                    UserCoupon(2L, userId, 2L, CouponStatus.USED, now.format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter), now.format(dateTimeFormatter)),
                    UserCoupon(3L, userId, 3L, CouponStatus.AVAILABLE, now.format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter))
                )
                val coupon1 = Coupon(1L, "쿠폰1", "설명", 10, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val coupon3 = Coupon(3L, "쿠폰3", "설명", 30, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                every { couponRepository.findUserCouponsByUserId(userId) } returns userCoupons
                every { couponRepository.findById(1L) } returns coupon1
                every { couponRepository.findById(3L) } returns coupon3

                // when
                val result = couponService.getUserCoupons(userId, CouponStatus.AVAILABLE)

                // then
                result.coupons shouldHaveSize 2
                result.coupons.all { it.status == "AVAILABLE" } shouldBe true

                verify(exactly = 1) { couponRepository.findUserCouponsByUserId(userId) }
            }

            it("USED 상태의 쿠폰만 조회한다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()
                val userCoupons = listOf(
                    UserCoupon(1L, userId, 1L, CouponStatus.AVAILABLE, now.format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter)),
                    UserCoupon(2L, userId, 2L, CouponStatus.USED, now.format(dateTimeFormatter), now.plusDays(20).format(dateTimeFormatter), now.format(dateTimeFormatter))
                )
                val coupon2 = Coupon(2L, "쿠폰2", "설명", 20, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                every { couponRepository.findUserCouponsByUserId(userId) } returns userCoupons
                every { couponRepository.findById(2L) } returns coupon2

                // when
                val result = couponService.getUserCoupons(userId, CouponStatus.USED)

                // then
                result.coupons shouldHaveSize 1
                result.coupons[0].status shouldBe "USED"

                verify(exactly = 1) { couponRepository.findUserCouponsByUserId(userId) }
            }
        }
    }

    describe("CouponService 단위 테스트 - getUserCoupon") {
        context("정상 케이스") {
            it("사용자의 특정 쿠폰 상세 정보를 조회한다") {
                // given
                val userId = 100L
                val userCouponId = 1L
                val now = LocalDateTime.now()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(20).format(dateTimeFormatter)
                )
                val coupon = Coupon(1L, "쿠폰1", "상세 설명", 10, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                every { couponRepository.findUserCouponByIdAndUserId(userCouponId, userId) } returns userCoupon
                every { couponRepository.findById(1L) } returns coupon

                // when
                val result = couponService.getUserCoupon(userId, userCouponId)

                // then
                result.id shouldBe userCouponId
                result.userId shouldBe userId
                result.couponId shouldBe 1L
                result.couponName shouldBe "쿠폰1"
                result.description shouldBe "상세 설명"
                result.discountRate shouldBe 10
                result.status shouldBe CouponStatus.AVAILABLE
                result.canUse shouldBe true
                result.isExpired shouldBe false

                verify(exactly = 1) { couponRepository.findUserCouponByIdAndUserId(userCouponId, userId) }
                verify(exactly = 1) { couponRepository.findById(1L) }
            }

            it("사용된 쿠폰은 canUse가 false이다") {
                // given
                val userId = 100L
                val userCouponId = 1L
                val now = LocalDateTime.now()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = 1L,
                    status = CouponStatus.USED,
                    issuedAt = now.minusDays(10).format(dateTimeFormatter),
                    expiresAt = now.plusDays(20).format(dateTimeFormatter),
                    usedAt = now.format(dateTimeFormatter)
                )
                val coupon = Coupon(1L, "쿠폰1", "설명", 10, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                every { couponRepository.findUserCouponByIdAndUserId(userCouponId, userId) } returns userCoupon
                every { couponRepository.findById(1L) } returns coupon

                // when
                val result = couponService.getUserCoupon(userId, userCouponId)

                // then
                result.status shouldBe CouponStatus.USED
                result.canUse shouldBe false
                result.usedAt shouldNotBe null

                verify(exactly = 1) { couponRepository.findUserCouponByIdAndUserId(userCouponId, userId) }
            }

            it("만료된 쿠폰은 canUse가 false이다") {
                // given
                val userId = 100L
                val userCouponId = 1L
                val now = LocalDateTime.now()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.minusDays(40).format(dateTimeFormatter),
                    expiresAt = now.minusDays(10).format(dateTimeFormatter) // 만료됨
                )
                val coupon = Coupon(1L, "쿠폰1", "설명", 10, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                every { couponRepository.findUserCouponByIdAndUserId(userCouponId, userId) } returns userCoupon
                every { couponRepository.findById(1L) } returns coupon

                // when
                val result = couponService.getUserCoupon(userId, userCouponId)

                // then
                result.canUse shouldBe false
                result.isExpired shouldBe true

                verify(exactly = 1) { couponRepository.findUserCouponByIdAndUserId(userCouponId, userId) }
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 UserCoupon 조회 시 UserCouponNotFoundException을 발생시킨다") {
                // given
                val userId = 100L
                val invalidUserCouponId = 999L

                every { couponRepository.findUserCouponByIdAndUserId(invalidUserCouponId, userId) } returns null

                // when & then
                val exception = shouldThrow<UserCouponNotFoundException> {
                    couponService.getUserCoupon(userId, invalidUserCouponId)
                }
                exception.message shouldContain "User Coupon Not found"

                verify(exactly = 1) { couponRepository.findUserCouponByIdAndUserId(invalidUserCouponId, userId) }
            }
        }
    }
})
