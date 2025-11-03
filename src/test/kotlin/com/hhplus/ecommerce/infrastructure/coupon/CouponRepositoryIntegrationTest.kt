package com.hhplus.ecommerce.infrastructure.coupon

import com.hhplus.ecommerce.model.coupon.Coupon
import com.hhplus.ecommerce.model.coupon.UserCoupon
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CouponRepositoryIntegrationTest : DescribeSpec({
    lateinit var couponRepository: CouponRepository
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    beforeEach {
        couponRepository = CouponRepositoryImpl()
    }

    describe("CouponRepository 통합 테스트 - Coupon 관리") {
        context("findById") {
            it("존재하는 쿠폰 ID로 쿠폰을 조회한다") {
                // given
                val couponId = 1L

                // when
                val coupon = couponRepository.findById(couponId)

                // then
                coupon shouldNotBe null
                coupon!!.id shouldBe couponId
                coupon.name shouldBe "신규 가입 환영 쿠폰"
                coupon.discountRate shouldBe 10
                coupon.totalQuantity shouldBe 100
                coupon.validityDays shouldBe 30
            }

            it("다양한 할인율의 쿠폰을 조회할 수 있다") {
                // when
                val coupon10 = couponRepository.findById(1L) // 10%
                val coupon20 = couponRepository.findById(2L) // 20%
                val coupon30 = couponRepository.findById(4L) // 30%

                // then
                coupon10!!.discountRate shouldBe 10
                coupon20!!.discountRate shouldBe 20
                coupon30!!.discountRate shouldBe 30
            }

            it("존재하지 않는 쿠폰 ID로 조회 시 null을 반환한다") {
                // given
                val invalidCouponId = 999L

                // when
                val coupon = couponRepository.findById(invalidCouponId)

                // then
                coupon shouldBe null
            }
        }

        context("findAll") {
            it("모든 쿠폰을 조회한다") {
                // when
                val coupons = couponRepository.findAll()

                // then
                coupons shouldHaveSize 5
                coupons.all { it.id > 0 } shouldBe true
                coupons.all { it.discountRate > 0 } shouldBe true
            }

            it("조회된 쿠폰들이 다양한 할인율을 포함한다") {
                // when
                val coupons = couponRepository.findAll()

                // then
                val discountRates = coupons.map { it.discountRate }.toSet()
                discountRates shouldBe setOf(5, 10, 15, 20, 30)
            }

            it("조회된 각 쿠폰이 필수 정보를 모두 포함한다") {
                // when
                val coupons = couponRepository.findAll()

                // then
                coupons.forEach { coupon ->
                    coupon.id shouldNotBe null
                    coupon.name shouldNotBe ""
                    coupon.description shouldNotBe ""
                    coupon.discountRate shouldNotBe 0
                    coupon.totalQuantity shouldNotBe 0
                    coupon.startDate shouldNotBe ""
                    coupon.endDate shouldNotBe ""
                    coupon.validityDays shouldNotBe 0
                    coupon.createdAt shouldNotBe ""
                }
            }
        }

        context("findAvailableCoupons") {
            it("발급 가능한 쿠폰만 조회한다 (기간 내 + 재고 있음)") {
                // when
                val availableCoupons = couponRepository.findAvailableCoupons()

                // then
                availableCoupons.size shouldNotBe 0
                availableCoupons.forEach { coupon ->
                    // 재고가 있어야 함
                    (coupon.issuedQuantity < coupon.totalQuantity) shouldBe true

                    // 발급 기간 내여야 함
                    val today = LocalDate.now()
                    val startDate = LocalDate.parse(coupon.startDate, dateFormatter)
                    val endDate = LocalDate.parse(coupon.endDate, dateFormatter)
                    val isInPeriod = !today.isBefore(startDate) && !today.isAfter(endDate)
                    isInPeriod shouldBe true
                }
            }

            it("품절된 쿠폰은 제외된다") {
                // given
                val soldOutCoupon = couponRepository.findById(4L) // 30/30 품절

                // when
                val availableCoupons = couponRepository.findAvailableCoupons()

                // then
                availableCoupons.none { it.id == soldOutCoupon!!.id } shouldBe true
            }

            it("발급 기간이 지난 쿠폰은 제외된다") {
                // given
                val expiredCoupon = couponRepository.findById(4L) // 발급 기간 종료

                // when
                val availableCoupons = couponRepository.findAvailableCoupons()

                // then
                availableCoupons.none { it.id == expiredCoupon!!.id } shouldBe true
            }

            it("발급 기간이 시작되지 않은 쿠폰은 제외된다") {
                // given
                val futureCoupon = couponRepository.findById(5L) // 내일 시작

                // when
                val availableCoupons = couponRepository.findAvailableCoupons()

                // then
                availableCoupons.none { it.id == futureCoupon!!.id } shouldBe true
            }
        }

        context("save") {
            it("새로운 쿠폰을 저장한다") {
                // given
                val newCoupon = Coupon(
                    id = 100L,
                    name = "신규 쿠폰",
                    description = "테스트 쿠폰",
                    discountRate = 25,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDate.now().format(dateFormatter),
                    endDate = LocalDate.now().plusDays(30).format(dateFormatter),
                    validityDays = 30,
                    createdAt = LocalDateTime.now().format(dateTimeFormatter)
                )

                // when
                val saved = couponRepository.save(newCoupon)

                // then
                saved.id shouldBe 100L
                saved.name shouldBe "신규 쿠폰"

                // 저장 후 조회 가능
                val found = couponRepository.findById(100L)
                found shouldNotBe null
                found!!.name shouldBe "신규 쿠폰"
            }

            it("기존 쿠폰의 발급 수량을 업데이트한다") {
                // given
                val couponId = 1L
                val originalCoupon = couponRepository.findById(couponId)!!
                val originalIssuedQuantity = originalCoupon.issuedQuantity

                // when - 발급 수량 증가
                originalCoupon.issuedQuantity++
                couponRepository.save(originalCoupon)

                // then
                val updated = couponRepository.findById(couponId)!!
                updated.issuedQuantity shouldBe (originalIssuedQuantity + 1)
            }

            it("쿠폰 발급 시나리오: 발급 수량이 증가한다") {
                // given
                val couponId = 2L
                val coupon = couponRepository.findById(couponId)!!
                val originalIssuedQuantity = coupon.issuedQuantity

                // when - 3명에게 발급
                coupon.issuedQuantity++
                couponRepository.save(coupon)
                coupon.issuedQuantity++
                couponRepository.save(coupon)
                coupon.issuedQuantity++
                couponRepository.save(coupon)

                // then
                val updated = couponRepository.findById(couponId)!!
                updated.issuedQuantity shouldBe (originalIssuedQuantity + 3)
            }
        }
    }

    describe("CouponRepository 통합 테스트 - UserCoupon 관리") {
        context("saveUserCoupon & findUserCoupon") {
            it("사용자 쿠폰을 저장하고 조회한다") {
                // given
                val userId = 100L
                val couponId = 1L
                val now = LocalDateTime.now()
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )

                // when
                couponRepository.saveUserCoupon(userCoupon)
                val found = couponRepository.findUserCoupon(userId, couponId)

                // then
                found shouldNotBe null
                found!!.id shouldBe 1L
                found.userId shouldBe userId
                found.couponId shouldBe couponId
                found.status shouldBe CouponStatus.AVAILABLE
            }

            it("중복 발급을 체크할 수 있다") {
                // given
                val userId = 100L
                val couponId = 1L
                val now = LocalDateTime.now()
                val userCoupon = UserCoupon(
                    id = 1L,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )

                // when - 첫 발급
                couponRepository.saveUserCoupon(userCoupon)

                // then - 이미 발급받았음
                val existing = couponRepository.findUserCoupon(userId, couponId)
                existing shouldNotBe null
            }

            it("다른 사용자는 같은 쿠폰을 발급받을 수 있다") {
                // given
                val couponId = 1L
                val now = LocalDateTime.now()
                val userCoupon1 = UserCoupon(
                    id = 1L,
                    userId = 100L,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )
                val userCoupon2 = UserCoupon(
                    id = 2L,
                    userId = 200L,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )

                // when
                couponRepository.saveUserCoupon(userCoupon1)
                couponRepository.saveUserCoupon(userCoupon2)

                // then
                val found1 = couponRepository.findUserCoupon(100L, couponId)
                val found2 = couponRepository.findUserCoupon(200L, couponId)
                found1 shouldNotBe null
                found2 shouldNotBe null
                found1!!.userId shouldBe 100L
                found2!!.userId shouldBe 200L
            }

            it("발급받지 않은 쿠폰 조회 시 null을 반환한다") {
                // when
                val notFound = couponRepository.findUserCoupon(999L, 1L)

                // then
                notFound shouldBe null
            }
        }

        context("findUserCouponsByUserId") {
            it("사용자의 모든 쿠폰을 조회한다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()
                val userCoupon1 = UserCoupon(1L, userId, 1L, CouponStatus.AVAILABLE, now.format(dateTimeFormatter), now.plusDays(30).format(dateTimeFormatter))
                val userCoupon2 = UserCoupon(2L, userId, 2L, CouponStatus.USED, now.format(dateTimeFormatter), now.plusDays(30).format(dateTimeFormatter), now.format(dateTimeFormatter))
                val userCoupon3 = UserCoupon(3L, userId, 3L, CouponStatus.EXPIRED, now.minusDays(40).format(dateTimeFormatter), now.minusDays(10).format(dateTimeFormatter))

                couponRepository.saveUserCoupon(userCoupon1)
                couponRepository.saveUserCoupon(userCoupon2)
                couponRepository.saveUserCoupon(userCoupon3)

                // when
                val userCoupons = couponRepository.findUserCouponsByUserId(userId)

                // then
                userCoupons shouldHaveSize 3
                userCoupons.all { it.userId == userId } shouldBe true
            }

            it("쿠폰을 발급받지 않은 사용자는 빈 목록을 반환한다") {
                // when
                val userCoupons = couponRepository.findUserCouponsByUserId(999L)

                // then
                userCoupons.shouldBeEmpty()
            }

            it("다양한 상태의 쿠폰을 조회할 수 있다") {
                // given
                val userId = 100L
                val now = LocalDateTime.now()
                val available = UserCoupon(1L, userId, 1L, CouponStatus.AVAILABLE, now.format(dateTimeFormatter), now.plusDays(30).format(dateTimeFormatter))
                val used = UserCoupon(2L, userId, 2L, CouponStatus.USED, now.format(dateTimeFormatter), now.plusDays(30).format(dateTimeFormatter), now.format(dateTimeFormatter))
                val expired = UserCoupon(3L, userId, 3L, CouponStatus.EXPIRED, now.minusDays(40).format(dateTimeFormatter), now.minusDays(10).format(dateTimeFormatter))

                couponRepository.saveUserCoupon(available)
                couponRepository.saveUserCoupon(used)
                couponRepository.saveUserCoupon(expired)

                // when
                val userCoupons = couponRepository.findUserCouponsByUserId(userId)

                // then
                val statuses = userCoupons.map { it.status }.toSet()
                statuses shouldBe setOf(CouponStatus.AVAILABLE, CouponStatus.USED, CouponStatus.EXPIRED)
            }
        }

        context("findUserCouponByIdAndUserId") {
            it("사용자의 특정 쿠폰을 조회한다") {
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
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )

                couponRepository.saveUserCoupon(userCoupon)

                // when
                val found = couponRepository.findUserCouponByIdAndUserId(userCouponId, userId)

                // then
                found shouldNotBe null
                found!!.id shouldBe userCouponId
                found.userId shouldBe userId
            }

            it("다른 사용자의 쿠폰은 조회할 수 없다") {
                // given
                val userId = 100L
                val otherUserId = 200L
                val userCouponId = 1L
                val now = LocalDateTime.now()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = 1L,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )

                couponRepository.saveUserCoupon(userCoupon)

                // when
                val notFound = couponRepository.findUserCouponByIdAndUserId(userCouponId, otherUserId)

                // then
                notFound shouldBe null
            }

            it("존재하지 않는 UserCoupon 조회 시 null을 반환한다") {
                // when
                val notFound = couponRepository.findUserCouponByIdAndUserId(999L, 100L)

                // then
                notFound shouldBe null
            }
        }

        context("generateUserCouponId") {
            it("UserCoupon ID를 순차적으로 생성한다") {
                // when
                val id1 = couponRepository.generateUserCouponId()
                val id2 = couponRepository.generateUserCouponId()
                val id3 = couponRepository.generateUserCouponId()

                // then
                id2 shouldBe (id1 + 1)
                id3 shouldBe (id2 + 1)
            }
        }
    }

    describe("CouponRepository 통합 테스트 - 복합 시나리오") {
        context("쿠폰 발급 전체 플로우") {
            it("쿠폰 조회 -> 검증 -> 발급 -> UserCoupon 생성") {
                // 1. 쿠폰 조회
                val couponId = 1L
                val coupon = couponRepository.findById(couponId)
                coupon shouldNotBe null

                // 2. 발급 가능 여부 검증
                val originalIssuedQuantity = coupon!!.issuedQuantity
                val canIssue = originalIssuedQuantity < coupon.totalQuantity
                canIssue shouldBe true

                // 3. 중복 발급 체크
                val userId = 100L
                val existing = couponRepository.findUserCoupon(userId, couponId)
                existing shouldBe null

                // 4. 발급 수량 증가
                coupon.issuedQuantity++
                couponRepository.save(coupon)

                // 5. UserCoupon 생성
                val now = LocalDateTime.now()
                val userCouponId = couponRepository.generateUserCouponId()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(coupon.validityDays.toLong()).format(dateTimeFormatter)
                )
                couponRepository.saveUserCoupon(userCoupon)

                // 6. 검증
                val updatedCoupon = couponRepository.findById(couponId)!!
                updatedCoupon.issuedQuantity shouldBe (originalIssuedQuantity + 1)

                val savedUserCoupon = couponRepository.findUserCoupon(userId, couponId)
                savedUserCoupon shouldNotBe null
                savedUserCoupon!!.status shouldBe CouponStatus.AVAILABLE
            }

            it("여러 사용자에게 동일한 쿠폰을 발급한다") {
                // given
                val couponId = 2L
                val userIds = listOf(100L, 200L, 300L)
                val now = LocalDateTime.now()

                // when
                userIds.forEach { userId ->
                    // 쿠폰 발급
                    val coupon = couponRepository.findById(couponId)!!
                    coupon.issuedQuantity++
                    couponRepository.save(coupon)

                    // UserCoupon 생성
                    val userCouponId = couponRepository.generateUserCouponId()
                    val userCoupon = UserCoupon(
                        id = userCouponId,
                        userId = userId,
                        couponId = couponId,
                        status = CouponStatus.AVAILABLE,
                        issuedAt = now.format(dateTimeFormatter),
                        expiresAt = now.plusDays(30).format(dateTimeFormatter)
                    )
                    couponRepository.saveUserCoupon(userCoupon)
                }

                // then
                userIds.forEach { userId ->
                    val userCoupon = couponRepository.findUserCoupon(userId, couponId)
                    userCoupon shouldNotBe null
                    userCoupon!!.userId shouldBe userId
                    userCoupon.couponId shouldBe couponId
                }
            }

            it("사용자가 여러 쿠폰을 발급받는다") {
                // given
                val userId = 100L
                val couponIds = listOf(1L, 2L, 3L)
                val now = LocalDateTime.now()

                // when
                couponIds.forEach { couponId ->
                    val coupon = couponRepository.findById(couponId)!!
                    coupon.issuedQuantity++
                    couponRepository.save(coupon)

                    val userCouponId = couponRepository.generateUserCouponId()
                    val userCoupon = UserCoupon(
                        id = userCouponId,
                        userId = userId,
                        couponId = couponId,
                        status = CouponStatus.AVAILABLE,
                        issuedAt = now.format(dateTimeFormatter),
                        expiresAt = now.plusDays(30).format(dateTimeFormatter)
                    )
                    couponRepository.saveUserCoupon(userCoupon)
                }

                // then
                val userCoupons = couponRepository.findUserCouponsByUserId(userId)
                userCoupons shouldHaveSize 3
                userCoupons.map { it.couponId }.toSet() shouldBe couponIds.toSet()
            }
        }

        context("쿠폰 상태 변경 시나리오") {
            it("쿠폰 사용: AVAILABLE -> USED") {
                // given
                val userId = 100L
                val couponId = 1L
                val now = LocalDateTime.now()
                val userCouponId = couponRepository.generateUserCouponId()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter)
                )
                couponRepository.saveUserCoupon(userCoupon)

                // when - 쿠폰 사용
                userCoupon.status = CouponStatus.USED
                userCoupon.usedAt = now.format(dateTimeFormatter)
                couponRepository.saveUserCoupon(userCoupon)

                // then
                val updated = couponRepository.findUserCouponByIdAndUserId(userCouponId, userId)!!
                updated.status shouldBe CouponStatus.USED
                updated.usedAt shouldNotBe null
            }

            it("결제 실패 시 쿠폰 복원: USED -> AVAILABLE") {
                // given - 사용된 쿠폰
                val userId = 100L
                val couponId = 1L
                val now = LocalDateTime.now()
                val userCouponId = couponRepository.generateUserCouponId()
                val userCoupon = UserCoupon(
                    id = userCouponId,
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.USED,
                    issuedAt = now.format(dateTimeFormatter),
                    expiresAt = now.plusDays(30).format(dateTimeFormatter),
                    usedAt = now.format(dateTimeFormatter)
                )
                couponRepository.saveUserCoupon(userCoupon)

                // when - 결제 실패로 쿠폰 복원
                userCoupon.status = CouponStatus.AVAILABLE
                userCoupon.usedAt = null
                couponRepository.saveUserCoupon(userCoupon)

                // then
                val restored = couponRepository.findUserCouponByIdAndUserId(userCouponId, userId)!!
                restored.status shouldBe CouponStatus.AVAILABLE
                restored.usedAt shouldBe null
            }
        }
    }
})
