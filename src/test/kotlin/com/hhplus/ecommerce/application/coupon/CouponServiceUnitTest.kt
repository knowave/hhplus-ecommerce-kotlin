package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import com.hhplus.ecommerce.common.exception.InvalidCouponDateException
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CouponServiceUnitTest : DescribeSpec({
    lateinit var couponRepository: CouponRepository
    lateinit var lockManager: com.hhplus.ecommerce.common.lock.LockManager
    lateinit var couponService: CouponServiceImpl
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    beforeEach {
        couponRepository = mockk()
        lockManager = com.hhplus.ecommerce.common.lock.LockManager()
        couponService = CouponServiceImpl(couponRepository, lockManager)
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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns userCouponId
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, command)

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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns 1L
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, command)

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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns 1L
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, command)

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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { couponRepository.generateUserCouponId() } returns 1L
                every { couponRepository.saveUserCoupon(any()) } answers { firstArg() }

                // when
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.couponId shouldBe couponId

                verify(exactly = 1) { couponRepository.findById(couponId) }
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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns existingUserCoupon

                // when & then
                val exception = shouldThrow<CouponAlreadyIssuedException> {
                    couponService.issueCoupon(couponId, command)
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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<InvalidCouponDateException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "The coupon issuance period has not started."

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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<InvalidCouponDateException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "The coupon issuance period has ended."

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
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findById(couponId) } returns coupon
                every { couponRepository.findUserCoupon(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<CouponSoldOutException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "Coupon sold out"

                verify(exactly = 1) { couponRepository.findById(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }
    }
})
