package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.CouponOutOfStockException
import com.hhplus.ecommerce.common.exception.InvalidCouponDateException
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID

class CouponAsyncIssuanceUnitTest : DescribeSpec({
    lateinit var couponRepository: CouponJpaRepository
    lateinit var userCouponRepository: UserCouponJpaRepository
    lateinit var redisTemplate: RedisTemplate<String, String>
    lateinit var setOperations: SetOperations<String, String>
    lateinit var valueOperations: ValueOperations<String, String>
    lateinit var listOperations: ListOperations<String, String>
    lateinit var couponService: CouponServiceImpl

    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    beforeEach {
        couponRepository = mockk()
        userCouponRepository = mockk()
        redisTemplate = mockk()
        setOperations = mockk()
        valueOperations = mockk()
        listOperations = mockk()

        every { redisTemplate.opsForSet() } returns setOperations
        every { redisTemplate.opsForValue() } returns valueOperations
        every { redisTemplate.opsForList() } returns listOperations

        couponService = CouponServiceImpl(couponRepository, userCouponRepository, redisTemplate)
    }

    describe("CouponService 비동기 발급 요청 테스트 - requestCouponIssuance") {
        context("정상 케이스") {
            it("Redis Queue에 발급 요청을 적재하고 QUEUED 상태를 반환한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()

                val coupon = spyk(Coupon(
                    name = "선착순 쿠폰",
                    description = "비동기 발급 테스트",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                ))
                every { coupon.id } returns couponId

                val command = IssueCouponCommand(userId = userId)

                // Mock 설정
                every { setOperations.isMember("coupon:$couponId:users", userId.toString()) } returns false
                every { couponRepository.findById(couponId) } returns Optional.of(coupon)
                every { valueOperations.increment("coupon:$couponId:count") } returns 1L
                every { setOperations.add("coupon:$couponId:users", userId.toString()) } returns 1L
                every { listOperations.rightPush("coupon:issue:queue", "$couponId:$userId") } returns 1L

                // when
                val result = couponService.requestCouponIssuance(couponId, command)

                // then
                result.userId shouldBe userId
                result.couponId shouldBe couponId
                result.status shouldBe "QUEUED"
                result.couponName shouldBe "선착순 쿠폰"
                result.discountRate shouldBe 10
                result.remainingQuantity shouldBe 99 // 100 - 1
                result.totalQuantity shouldBe 100

                verify(exactly = 1) { setOperations.isMember("coupon:$couponId:users", userId.toString()) }
                verify(exactly = 1) { valueOperations.increment("coupon:$couponId:count") }
                verify(exactly = 1) { setOperations.add("coupon:$couponId:users", userId.toString()) }
                verify(exactly = 1) { listOperations.rightPush("coupon:issue:queue", "$couponId:$userId") }
            }
        }

        context("예외 케이스 - 중복 발급") {
            it("이미 발급 요청한 사용자인 경우 CouponAlreadyIssuedException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val command = IssueCouponCommand(userId = userId)

                // Redis Set에 이미 존재
                every { setOperations.isMember("coupon:$couponId:users", userId.toString()) } returns true

                // when & then
                shouldThrow<CouponAlreadyIssuedException> {
                    couponService.requestCouponIssuance(couponId, command)
                }

                verify(exactly = 1) { setOperations.isMember("coupon:$couponId:users", userId.toString()) }
                verify(exactly = 0) { valueOperations.increment(any()) }
            }
        }

        context("예외 케이스 - 재고 소진") {
            it("재고가 모두 소진된 경우 CouponOutOfStockException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()

                val coupon = spyk(Coupon(
                    name = "선착순 쿠폰",
                    description = "재고 소진 테스트",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                ))
                every { coupon.id } returns couponId

                val command = IssueCouponCommand(userId = userId)

                // Mock 설정 - 재고 초과 (101 > 100)
                every { setOperations.isMember("coupon:$couponId:users", userId.toString()) } returns false
                every { couponRepository.findById(couponId) } returns Optional.of(coupon)
                every { valueOperations.increment("coupon:$couponId:count") } returns 101L
                every { valueOperations.decrement("coupon:$couponId:count") } returns 100L

                // when & then
                shouldThrow<CouponOutOfStockException> {
                    couponService.requestCouponIssuance(couponId, command)
                }

                verify(exactly = 1) { valueOperations.increment("coupon:$couponId:count") }
                verify(exactly = 1) { valueOperations.decrement("coupon:$couponId:count") }
            }
        }

        context("예외 케이스 - 발급 기간") {
            it("발급 기간이 아닌 경우 InvalidCouponDateException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()

                val coupon = spyk(Coupon(
                    name = "미래 쿠폰",
                    description = "발급 기간 전",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.plusDays(1).format(dateFormatter) + " 00:00:00", dateTimeFormatter), // 내일 시작
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                ))
                every { coupon.id } returns couponId

                val command = IssueCouponCommand(userId = userId)

                // Mock 설정
                every { setOperations.isMember("coupon:$couponId:users", userId.toString()) } returns false
                every { couponRepository.findById(couponId) } returns Optional.of(coupon)

                // when & then
                shouldThrow<InvalidCouponDateException> {
                    couponService.requestCouponIssuance(couponId, command)
                }

                verify(exactly = 0) { valueOperations.increment(any()) }
            }
        }

        context("예외 케이스 - 쿠폰 없음") {
            it("존재하지 않는 쿠폰인 경우 CouponNotFoundException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val command = IssueCouponCommand(userId = userId)

                // Mock 설정
                every { setOperations.isMember("coupon:$couponId:users", userId.toString()) } returns false
                every { couponRepository.findById(couponId) } returns Optional.empty()

                // when & then
                shouldThrow<CouponNotFoundException> {
                    couponService.requestCouponIssuance(couponId, command)
                }
            }
        }
    }
})

