package com.hhplus.ecommerce.model.coupon

import com.hhplus.ecommerce.infrastructure.coupon.CouponStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CouponUnitTest : DescribeSpec({
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    describe("Coupon 도메인 모델 단위 테스트") {
        context("Coupon 객체 생성") {
            it("Coupon 객체가 정상적으로 생성된다") {
                // given
                val couponId = 1L
                val name = "신규 회원 할인 쿠폰"
                val description = "신규 가입 회원을 위한 특별 할인"
                val discountRate = 10
                val totalQuantity = 100
                val issuedQuantity = 0
                val startDate = "2025-11-01"
                val endDate = "2025-11-30"
                val validityDays = 30
                val createdAt = "2025-10-01 00:00:00"

                // when
                val coupon = Coupon(
                    id = couponId,
                    name = name,
                    description = description,
                    discountRate = discountRate,
                    totalQuantity = totalQuantity,
                    issuedQuantity = issuedQuantity,
                    startDate = startDate,
                    endDate = endDate,
                    validityDays = validityDays,
                    createdAt = createdAt
                )

                // then
                coupon shouldNotBe null
                coupon.id shouldBe couponId
                coupon.name shouldBe name
                coupon.description shouldBe description
                coupon.discountRate shouldBe discountRate
                coupon.totalQuantity shouldBe totalQuantity
                coupon.issuedQuantity shouldBe issuedQuantity
                coupon.startDate shouldBe startDate
                coupon.endDate shouldBe endDate
                coupon.validityDays shouldBe validityDays
                coupon.createdAt shouldBe createdAt
            }

            it("다양한 할인율(10%, 20%, 30%)로 Coupon을 생성할 수 있다") {
                // when
                val coupon10 = Coupon(1L, "10% 할인", "설명", 10, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val coupon20 = Coupon(2L, "20% 할인", "설명", 20, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val coupon30 = Coupon(3L, "30% 할인", "설명", 30, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                // then
                coupon10.discountRate shouldBe 10
                coupon20.discountRate shouldBe 20
                coupon30.discountRate shouldBe 30
            }

            it("발급 수량이 0인 새로운 쿠폰을 생성할 수 있다") {
                // when
                val coupon = Coupon(
                    id = 1L,
                    name = "신규 쿠폰",
                    description = "아직 발급되지 않음",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = "2025-11-01",
                    endDate = "2025-11-30",
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                // then
                coupon.issuedQuantity shouldBe 0
            }
        }

        context("Coupon 속성 변경") {
            it("Coupon의 발급 수량을 증가시킬 수 있다") {
                // given
                val coupon = Coupon(
                    id = 1L,
                    name = "테스트 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 50,
                    startDate = "2025-11-01",
                    endDate = "2025-11-30",
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                // when - 발급 수량 증가
                coupon.issuedQuantity++

                // then
                coupon.issuedQuantity shouldBe 51
            }

            it("발급 수량이 총 수량에 도달할 수 있다") {
                // given
                val coupon = Coupon(
                    id = 1L,
                    name = "테스트 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 99,
                    startDate = "2025-11-01",
                    endDate = "2025-11-30",
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                // when - 마지막 발급
                coupon.issuedQuantity++

                // then
                coupon.issuedQuantity shouldBe coupon.totalQuantity
            }
        }

        context("비즈니스 시나리오 테스트") {
            it("쿠폰 발급 시나리오: 발급 수량이 1씩 증가한다") {
                // given
                val coupon = Coupon(
                    id = 1L,
                    name = "선착순 쿠폰",
                    description = "선착순 100명",
                    discountRate = 20,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = "2025-11-01",
                    endDate = "2025-11-30",
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                // when - 3명에게 발급
                coupon.issuedQuantity++
                coupon.issuedQuantity++
                coupon.issuedQuantity++

                // then
                coupon.issuedQuantity shouldBe 3
            }

            it("잔여 수량을 계산할 수 있다") {
                // given
                val coupon = Coupon(
                    id = 1L,
                    name = "테스트 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 30,
                    startDate = "2025-11-01",
                    endDate = "2025-11-30",
                    validityDays = 30,
                    createdAt = "2025-10-01 00:00:00"
                )

                // when
                val remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity

                // then
                remainingQuantity shouldBe 70
            }

            it("품절 여부를 확인할 수 있다") {
                // given
                val soldOutCoupon = Coupon(1L, "품절 쿠폰", "설명", 10, 100, 100, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val availableCoupon = Coupon(2L, "재고 있는 쿠폰", "설명", 10, 100, 50, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                // when
                val isSoldOut1 = soldOutCoupon.issuedQuantity >= soldOutCoupon.totalQuantity
                val isSoldOut2 = availableCoupon.issuedQuantity >= availableCoupon.totalQuantity

                // then
                isSoldOut1 shouldBe true
                isSoldOut2 shouldBe false
            }

            it("할인 금액을 계산할 수 있다") {
                // given
                val coupon = Coupon(1L, "20% 할인", "설명", 20, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val totalAmount = 100000L

                // when
                val discountAmount = totalAmount * coupon.discountRate / 100

                // then
                discountAmount shouldBe 20000L
            }
        }

        context("data class 동작") {
            it("동일한 값을 가진 Coupon 객체는 같다고 판단된다 (equals)") {
                // given
                val coupon1 = Coupon(1L, "쿠폰", "설명", 10, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")
                val coupon2 = Coupon(1L, "쿠폰", "설명", 10, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                // then
                coupon1 shouldBe coupon2
            }

            it("copy() 메서드로 일부 속성만 변경한 새 객체를 생성할 수 있다") {
                // given
                val coupon = Coupon(1L, "쿠폰", "설명", 10, 100, 0, "2025-11-01", "2025-11-30", 30, "2025-10-01 00:00:00")

                // when
                val copiedCoupon = coupon.copy(issuedQuantity = 50)

                // then
                copiedCoupon.id shouldBe coupon.id
                copiedCoupon.name shouldBe coupon.name
                copiedCoupon.issuedQuantity shouldBe 50
                copiedCoupon shouldNotBe coupon // issuedQuantity가 다르므로
            }
        }
    }

    describe("UserCoupon 도메인 모델 단위 테스트") {
        context("UserCoupon 객체 생성") {
            it("UserCoupon 객체가 정상적으로 생성된다") {
                // given
                val userCouponId = 1L
                val userId = 100L
                val couponId = 1L
                val status = CouponStatus.AVAILABLE
                val issuedAt = LocalDateTime.now().format(dateTimeFormatter)
                val expiresAt = LocalDateTime.now().plusDays(30).format(dateTimeFormatter)

                // when
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = couponId,
                    status = status,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                    usedAt = null
                )

                // then
                userCoupon shouldNotBe null
                userCoupon.id shouldBe userCouponId
                userCoupon.userId shouldBe userId
                userCoupon.couponId shouldBe couponId
                userCoupon.status shouldBe status
                userCoupon.issuedAt shouldBe issuedAt
                userCoupon.expiresAt shouldBe expiresAt
                userCoupon.usedAt shouldBe null
            }

            it("사용하지 않은 쿠폰은 usedAt이 null이다") {
                // when
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-12-01 10:00:00",
                    usedAt = null
                )

                // then
                userCoupon.usedAt shouldBe null
            }

            it("다양한 상태(AVAILABLE, USED, EXPIRED)로 UserCoupon을 생성할 수 있다") {
                // when
                val available = UserCoupon(1L, 100L, 1L, CouponStatus.AVAILABLE, "2025-11-01 10:00:00", "2025-12-01 10:00:00")
                val used = UserCoupon(2L, 100L, 1L, CouponStatus.USED, "2025-11-01 10:00:00", "2025-12-01 10:00:00", "2025-11-15 10:00:00")
                val expired = UserCoupon(3L, 100L, 1L, CouponStatus.EXPIRED, "2025-11-01 10:00:00", "2025-11-05 10:00:00")

                // then
                available.status shouldBe CouponStatus.AVAILABLE
                used.status shouldBe CouponStatus.USED
                expired.status shouldBe CouponStatus.EXPIRED
            }
        }

        context("UserCoupon 속성 변경") {
            it("UserCoupon의 상태를 AVAILABLE에서 USED로 변경할 수 있다") {
                // given
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-12-01 10:00:00"
                )

                // when - 쿠폰 사용
                userCoupon.status = CouponStatus.USED
                userCoupon.usedAt = LocalDateTime.now().format(dateTimeFormatter)

                // then
                userCoupon.status shouldBe CouponStatus.USED
                userCoupon.usedAt shouldNotBe null
            }

            it("UserCoupon의 상태를 AVAILABLE에서 EXPIRED로 변경할 수 있다") {
                // given
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-11-05 10:00:00"
                )

                // when - 만료 처리
                userCoupon.status = CouponStatus.EXPIRED

                // then
                userCoupon.status shouldBe CouponStatus.EXPIRED
            }

            it("사용된 쿠폰을 다시 AVAILABLE로 복원할 수 있다 (결제 실패 시)") {
                // given
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.USED,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-12-01 10:00:00",
                    usedAt = "2025-11-15 10:00:00"
                )

                // when - 결제 실패로 쿠폰 복원
                userCoupon.status = CouponStatus.AVAILABLE
                userCoupon.usedAt = null

                // then
                userCoupon.status shouldBe CouponStatus.AVAILABLE
                userCoupon.usedAt shouldBe null
            }
        }

        context("CouponStatus 열거형 동작") {
            it("모든 쿠폰 상태를 조회할 수 있다") {
                // when
                val statuses = CouponStatus.entries

                // then
                statuses.size shouldBe 3
                statuses shouldBe listOf(
                    CouponStatus.AVAILABLE,
                    CouponStatus.USED,
                    CouponStatus.EXPIRED
                )
            }

            it("문자열로 CouponStatus를 생성할 수 있다") {
                // when
                val status = CouponStatus.valueOf("AVAILABLE")

                // then
                status shouldBe CouponStatus.AVAILABLE
            }

            it("상태의 설명을 조회할 수 있다") {
                // when
                val availableDesc = CouponStatus.AVAILABLE.description
                val usedDesc = CouponStatus.USED.description
                val expiredDesc = CouponStatus.EXPIRED.description

                // then
                availableDesc shouldBe "사용 가능"
                usedDesc shouldBe "사용 완료"
                expiredDesc shouldBe "만료"
            }
        }

        context("비즈니스 시나리오 테스트") {
            it("쿠폰 사용 시나리오: AVAILABLE → USED, usedAt 설정") {
                // given
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-12-01 10:00:00"
                )

                // when - 주문 시 쿠폰 사용
                val usedTime = LocalDateTime.now().format(dateTimeFormatter)
                userCoupon.status = CouponStatus.USED
                userCoupon.usedAt = usedTime

                // then
                userCoupon.status shouldBe CouponStatus.USED
                userCoupon.usedAt shouldBe usedTime
            }

            it("쿠폰 복원 시나리오: USED → AVAILABLE, usedAt null (결제 실패)") {
                // given - 사용된 쿠폰
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.USED,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-12-01 10:00:00",
                    usedAt = "2025-11-15 10:00:00"
                )

                // when - 결제 실패로 쿠폰 복원
                userCoupon.status = CouponStatus.AVAILABLE
                userCoupon.usedAt = null

                // then
                userCoupon.status shouldBe CouponStatus.AVAILABLE
                userCoupon.usedAt shouldBe null
            }

            it("만료된 쿠폰은 복원하지 않는다 (EXPIRED 유지)") {
                // given - 만료된 쿠폰
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.EXPIRED,
                    issuedAt = "2025-11-01 10:00:00",
                    expiresAt = "2025-11-05 10:00:00"
                )

                // when - 복원 시도하지 않음 (비즈니스 로직에서 체크)
                val shouldRestore = userCoupon.status != CouponStatus.EXPIRED

                // then
                shouldRestore shouldBe false
                userCoupon.status shouldBe CouponStatus.EXPIRED
            }

            it("만료 여부를 확인할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val expiredCoupon = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.minusDays(40).format(dateTimeFormatter),
                    expiresAt = now.minusDays(10).format(dateTimeFormatter)
                )
                val validCoupon = UserCoupon(
                    id = 2L,
                    userId = 100L,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(20).format(dateTimeFormatter)
                )

                // when
                val expiresAtExpired = LocalDateTime.parse(expiredCoupon.expiresAt, dateTimeFormatter)
                val expiresAtValid = LocalDateTime.parse(validCoupon.expiresAt, dateTimeFormatter)
                val isExpired1 = expiresAtExpired.isBefore(now)
                val isExpired2 = expiresAtValid.isBefore(now)

                // then
                isExpired1 shouldBe true
                isExpired2 shouldBe false
            }
        }

        context("data class 동작") {
            it("동일한 값을 가진 UserCoupon 객체는 같다고 판단된다 (equals)") {
                // given
                val uc1 = UserCoupon(1L, 100L, 1L, CouponStatus.AVAILABLE, "2025-11-01 10:00:00", "2025-12-01 10:00:00")
                val uc2 = UserCoupon(1L, 100L, 1L, CouponStatus.AVAILABLE, "2025-11-01 10:00:00", "2025-12-01 10:00:00")

                // then
                uc1 shouldBe uc2
            }

            it("copy() 메서드로 일부 속성만 변경한 새 객체를 생성할 수 있다") {
                // given
                val userCoupon = UserCoupon(1L, 100L, 1L, CouponStatus.AVAILABLE, "2025-11-01 10:00:00", "2025-12-01 10:00:00")

                // when
                val copiedUserCoupon = userCoupon.copy(status = CouponStatus.USED, usedAt = "2025-11-15 10:00:00")

                // then
                copiedUserCoupon.id shouldBe userCoupon.id
                copiedUserCoupon.userId shouldBe userCoupon.userId
                copiedUserCoupon.status shouldBe CouponStatus.USED
                copiedUserCoupon.usedAt shouldBe "2025-11-15 10:00:00"
                copiedUserCoupon shouldNotBe userCoupon // status와 usedAt이 다르므로
            }
        }
    }
})
