package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import com.hhplus.ecommerce.common.exception.InvalidCouponDateException
import com.hhplus.ecommerce.common.lock.RedisDistributedLock
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.entity.UserCoupon
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.slot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CouponServiceUnitTest : DescribeSpec({
    lateinit var couponRepository: CouponJpaRepository
    lateinit var userCouponRepository: UserCouponJpaRepository
    lateinit var redisDistributedLock: RedisDistributedLock
    lateinit var couponService: CouponServiceImpl
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    beforeEach {
        couponRepository = mockk()
        userCouponRepository = mockk()
        redisDistributedLock = mockk()

        // Redis 분산 락 mock: executeWithLock 호출 시 전달된 람다를 즉시 실행
        every {
            redisDistributedLock.executeWithLock<IssueCouponResult>(any(), any(), any(), any())
        } answers {
            val action = arg<() -> IssueCouponResult>(3)
            action()
        }

        couponService = CouponServiceImpl(couponRepository, userCouponRepository, redisDistributedLock)
    }

    describe("CouponService 단위 테스트 - issueCoupon") {
        context("정상 케이스") {
            it("유효한 쿠폰을 정상적으로 발급한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = spyk(Coupon(
                    name = "신규 회원 할인",
                    description = "10% 할인 쿠폰",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                ))
                every { coupon.id } returns couponId
                val command = IssueCouponCommand(userId = userId)

                val mockUserCoupon = mockk<UserCoupon>()
                every { mockUserCoupon.id } returns userCouponId
                every { mockUserCoupon.userId } returns userId
                every { mockUserCoupon.couponId } returns couponId
                every { mockUserCoupon.status } returns CouponStatus.AVAILABLE
                every { mockUserCoupon.issuedAt } returns LocalDateTime.now()
                every { mockUserCoupon.expiresAt } returns LocalDateTime.now().plusDays(30)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { userCouponRepository.save(any()) } returns mockUserCoupon

                // when
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.userCouponId shouldNotBe null
                result.userId shouldBe userId
                result.couponId shouldBe couponId
                result.couponName shouldBe "신규 회원 할인"
                result.discountRate shouldBe 10
                result.status shouldBe "AVAILABLE"
                result.issuedAt shouldNotBe null
                result.expiresAt shouldNotBe null
                result.remainingQuantity shouldBe 99 // 100 - 1
                result.totalQuantity shouldBe 100

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
                verify(exactly = 1) { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) }
                verify(exactly = 1) { couponRepository.save(any()) }
                verify(exactly = 1) { userCouponRepository.save(any()) }
            }

            it("마지막 남은 쿠폰을 발급할 수 있다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = spyk(Coupon(
                    name = "마지막 쿠폰",
                    description = "설명",
                    discountRate = 20,
                    totalQuantity = 100,
                    issuedQuantity = 99, // 마지막 1개
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                ))
                every { coupon.id } returns couponId
                val command = IssueCouponCommand(userId = userId)

                val mockUserCoupon = mockk<UserCoupon>()
                every { mockUserCoupon.id } returns userCouponId
                every { mockUserCoupon.userId } returns userId
                every { mockUserCoupon.couponId } returns couponId
                every { mockUserCoupon.status } returns CouponStatus.AVAILABLE
                every { mockUserCoupon.issuedAt } returns LocalDateTime.now()
                every { mockUserCoupon.expiresAt } returns LocalDateTime.now().plusDays(30)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { userCouponRepository.save(any()) } returns mockUserCoupon

                // when
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.remainingQuantity shouldBe 0 // 100 - 100

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
                verify(exactly = 1) { couponRepository.save(any()) }
            }

            it("발급 기간 시작일에 쿠폰을 발급할 수 있다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = spyk(Coupon(
                    name = "오늘 시작",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter), // 오늘 시작
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                ))
                every { coupon.id } returns couponId
                val command = IssueCouponCommand(userId = userId)

                val mockUserCoupon = mockk<UserCoupon>()
                every { mockUserCoupon.id } returns userCouponId
                every { mockUserCoupon.userId } returns userId
                every { mockUserCoupon.couponId } returns couponId
                every { mockUserCoupon.status } returns CouponStatus.AVAILABLE
                every { mockUserCoupon.issuedAt } returns LocalDateTime.now()
                every { mockUserCoupon.expiresAt } returns LocalDateTime.now().plusDays(30)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { userCouponRepository.save(any()) } returns mockUserCoupon

                // when
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.couponId shouldBe couponId

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
            }

            it("발급 기간 종료일에 쿠폰을 발급할 수 있다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val userCouponId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = spyk(Coupon(
                    name = "오늘 종료",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.minusDays(30).format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.format(dateFormatter) + " 23:59:59", dateTimeFormatter), // 오늘 종료
                    validityDays = 30
                ))
                every { coupon.id } returns couponId
                val command = IssueCouponCommand(userId = userId)

                val mockUserCoupon = mockk<UserCoupon>()
                every { mockUserCoupon.id } returns userCouponId
                every { mockUserCoupon.userId } returns userId
                every { mockUserCoupon.couponId } returns couponId
                every { mockUserCoupon.status } returns CouponStatus.AVAILABLE
                every { mockUserCoupon.issuedAt } returns LocalDateTime.now()
                every { mockUserCoupon.expiresAt } returns LocalDateTime.now().plusDays(30)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null
                every { couponRepository.save(any()) } returns coupon
                every { userCouponRepository.save(any()) } returns mockUserCoupon

                // when
                val result = couponService.issueCoupon(couponId, command)

                // then
                result.couponId shouldBe couponId

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
            }
        }

        context("예외 케이스 - 중복 발급") {
            it("이미 발급받은 쿠폰을 재발급 시도 시 CouponAlreadyIssuedException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = Coupon(
                    name = "쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                )
                val existingUserCoupon = UserCoupon(
                    userId = userId,
                    couponId = couponId,
                    status = CouponStatus.AVAILABLE,
                    issuedAt = LocalDateTime.parse("2025-11-01 10:00:00", dateTimeFormatter),
                    expiresAt = LocalDateTime.parse("2025-12-01 10:00:00", dateTimeFormatter)
                )
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns existingUserCoupon

                // when & then
                val exception = shouldThrow<CouponAlreadyIssuedException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "User already has this coupon"

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
                verify(exactly = 1) { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }

        context("예외 케이스 - 발급 기간") {
            it("발급 기간 시작 전에 발급 시도 시 InvalidCouponDateException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = Coupon(
                    name = "미래 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.plusDays(1).format(dateFormatter) + " 00:00:00", dateTimeFormatter), // 내일 시작
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                )
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<InvalidCouponDateException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "The coupon issuance period has not started."

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }

            it("발급 기간 종료 후에 발급 시도 시 InvalidCouponDateException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = Coupon(
                    name = "만료된 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    startDate = LocalDateTime.parse(today.minusDays(30).format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.minusDays(1).format(dateFormatter) + " 23:59:59", dateTimeFormatter), // 어제 종료
                    validityDays = 30
                )
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<InvalidCouponDateException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "The coupon issuance period has ended."

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }

        context("예외 케이스 - 재고 부족") {
            it("품절된 쿠폰 발급 시도 시 CouponSoldOutException을 발생시킨다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val today = LocalDate.now()
                val coupon = Coupon(
                    name = "품절 쿠폰",
                    description = "설명",
                    discountRate = 10,
                    totalQuantity = 100,
                    issuedQuantity = 100, // 이미 모두 발급됨
                    startDate = LocalDateTime.parse(today.format(dateFormatter) + " 00:00:00", dateTimeFormatter),
                    endDate = LocalDateTime.parse(today.plusDays(30).format(dateFormatter) + " 23:59:59", dateTimeFormatter),
                    validityDays = 30
                )
                val command = IssueCouponCommand(userId = userId)

                every { couponRepository.findByIdWithLock(couponId) } returns Optional.of(coupon)
                every { userCouponRepository.findFirstByUserIdAndCouponId(userId, couponId) } returns null

                // when & then
                val exception = shouldThrow<CouponSoldOutException> {
                    couponService.issueCoupon(couponId, command)
                }
                exception.message shouldContain "Coupon sold out"

                verify(exactly = 1) { couponRepository.findByIdWithLock(couponId) }
                verify(exactly = 0) { couponRepository.save(any()) }
            }
        }
    }

    describe("CouponService 동시성 테스트 - ConcurrentHashMap 사용") {
        context("동시에 쿠폰 발급 시도") {
            it("100개의 쿠폰을 200명이 동시에 발급받을 때 정확히 100명만 성공한다 (ConcurrentHashMap)") {
                // given - ConcurrentHashMap으로 쿠폰 재고 관리
                val couponStock = ConcurrentHashMap<UUID, AtomicInteger>()
                val couponId = UUID.randomUUID()
                val totalQuantity = 100
                couponStock[couponId] = AtomicInteger(totalQuantity)

                // given - 발급된 사용자 관리 (중복 방지)
                val issuedUsers = ConcurrentHashMap.newKeySet<UUID>()

                // given - 결과 카운터
                val successCount = AtomicInteger(0)
                val failCount = AtomicInteger(0)

                // when - 200명이 동시에 쿠폰 발급 시도
                val threadCount = 200
                val executorService = Executors.newFixedThreadPool(threadCount)
                val latch = CountDownLatch(threadCount)

                for (i in 0 until threadCount) {
                    val userId = UUID.randomUUID()
                    executorService.submit {
                        try {
                            latch.countDown()
                            latch.await() // 모든 스레드가 준비될 때까지 대기

                            // 쿠폰 발급 시뮬레이션
                            val remainingStock = couponStock[couponId]!!

                            // 중복 발급 체크
                            if (issuedUsers.contains(userId)) {
                                failCount.incrementAndGet()
                                return@submit
                            }

                            // 재고 체크 및 차감 (원자적 연산)
                            val currentStock = remainingStock.get()
                            if (currentStock > 0) {
                                // compareAndSet으로 원자적으로 재고 차감
                                var success = false
                                var attempts = 0
                                while (!success && attempts < 10) {
                                    val current = remainingStock.get()
                                    if (current > 0) {
                                        success = remainingStock.compareAndSet(current, current - 1)
                                    } else {
                                        break
                                    }
                                    attempts++
                                }

                                if (success) {
                                    // 발급 성공
                                    issuedUsers.add(userId)
                                    successCount.incrementAndGet()
                                } else {
                                    failCount.incrementAndGet()
                                }
                            } else {
                                // 재고 부족
                                failCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            println("Unexpected exception: ${e.message}")
                            e.printStackTrace()
                            failCount.incrementAndGet()
                        }
                    }
                }

                executorService.shutdown()
                while (!executorService.isTerminated) {
                    Thread.sleep(100)
                }

                // then - 성공/실패 개수 검증
                println("=== ConcurrentHashMap 쿠폰 발급 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
                println("최종 재고: ${couponStock[couponId]?.get()}")
                println("발급된 사용자 수: ${issuedUsers.size}")

                successCount.get() shouldBe 100
                failCount.get() shouldBe 100
                couponStock[couponId]?.get() shouldBe 0 // 재고 0
                issuedUsers.size shouldBe 100 // 정확히 100명에게 발급
            }

            it("10개의 쿠폰을 50명이 동시에 발급받을 때 정확히 10명만 성공한다 (ConcurrentHashMap)") {
                // given - ConcurrentHashMap으로 쿠폰 재고 관리
                val couponStock = ConcurrentHashMap<UUID, AtomicInteger>()
                val couponId = UUID.randomUUID()
                val totalQuantity = 10
                couponStock[couponId] = AtomicInteger(totalQuantity)

                // given - 발급된 사용자 관리
                val issuedUsers = ConcurrentHashMap.newKeySet<UUID>()

                // given - 결과 카운터
                val successCount = AtomicInteger(0)
                val failCount = AtomicInteger(0)

                // when - 50명이 동시에 쿠폰 발급 시도
                val threadCount = 50
                val executorService = Executors.newFixedThreadPool(threadCount)
                val latch = CountDownLatch(threadCount)

                for (i in 0 until threadCount) {
                    val userId = UUID.randomUUID()
                    executorService.submit {
                        try {
                            latch.countDown()
                            latch.await()

                            val remainingStock = couponStock[couponId]!!

                            // 중복 발급 체크
                            if (issuedUsers.contains(userId)) {
                                failCount.incrementAndGet()
                                return@submit
                            }

                            // 재고 체크 및 차감
                            var success = false
                            var attempts = 0
                            while (!success && attempts < 10) {
                                val current = remainingStock.get()
                                if (current > 0) {
                                    success = remainingStock.compareAndSet(current, current - 1)
                                } else {
                                    break
                                }
                                attempts++
                            }

                            if (success) {
                                issuedUsers.add(userId)
                                successCount.incrementAndGet()
                            } else {
                                failCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            println("Unexpected exception: ${e.message}")
                            e.printStackTrace()
                            failCount.incrementAndGet()
                        }
                    }
                }

                executorService.shutdown()
                while (!executorService.isTerminated) {
                    Thread.sleep(100)
                }

                // then - 성공/실패 개수 검증
                println("=== ConcurrentHashMap 쿠폰 발급 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
                println("최종 재고: ${couponStock[couponId]?.get()}")
                println("발급된 사용자 수: ${issuedUsers.size}")

                successCount.get() shouldBe 10
                failCount.get() shouldBe 40
                couponStock[couponId]?.get() shouldBe 0
                issuedUsers.size shouldBe 10
            }

            it("동시성 문제가 없을 때 AtomicInteger의 compareAndSet이 정확하게 동작한다") {
                // given
                val counter = AtomicInteger(100)
                val threadCount = 100
                val executorService = Executors.newFixedThreadPool(threadCount)
                val latch = CountDownLatch(threadCount)
                val successCount = AtomicInteger(0)

                // when - 100개 스레드가 동시에 1씩 감소
                for (i in 0 until threadCount) {
                    executorService.submit {
                        try {
                            latch.countDown()
                            latch.await()

                            var success = false
                            var attempts = 0
                            while (!success && attempts < 10) {
                                val current = counter.get()
                                if (current > 0) {
                                    success = counter.compareAndSet(current, current - 1)
                                } else {
                                    break
                                }
                                attempts++
                            }

                            if (success) {
                                successCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                executorService.shutdown()
                while (!executorService.isTerminated) {
                    Thread.sleep(100)
                }

                // then
                println("=== AtomicInteger compareAndSet 테스트 ===")
                println("최종 값: ${counter.get()}, 성공 횟수: ${successCount.get()}")

                counter.get() shouldBe 0
                successCount.get() shouldBe 100
            }
        }
    }
})
