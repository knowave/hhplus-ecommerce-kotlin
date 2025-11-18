package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.*
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.lock.LockAcquisitionFailedException
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.common.exception.CouponSoldOutException
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    ]
)
@Import(
    com.hhplus.ecommerce.config.EmbeddedRedisConfig::class,
    com.hhplus.ecommerce.config.TestRedisConfig::class
)
class CouponServiceIntegrationTest(
    private val couponService: CouponService,
    private val userService: UserService,
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testCouponId: UUID
    private lateinit var testUserId: UUID
    private lateinit var testUser2Id: UUID

    // 새로운 트랜잭션에서 실행하고 커밋하는 헬퍼 메서드
    private fun <T> executeInNewTransaction(action: () -> T): T {
        val definition = DefaultTransactionDefinition().apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        val status = transactionManager.getTransaction(definition)
        return try {
            val result = action()
            entityManager.flush()
            transactionManager.commit(status)
            result
        } catch (e: Exception) {
            transactionManager.rollback(status)
            throw e
        }
    }

    init {
        beforeEach {
            // 사용자 생성
            val testUser = userService.createUser(CreateUserCommand(balance = 100000L))
            testUserId = testUser.id!!

            val testUser2 = userService.createUser(CreateUserCommand(balance = 100000L))
            testUser2Id = testUser2.id!!

            // 테스트용 쿠폰 생성
            val testCoupon = Coupon(
                name = "테스트 쿠폰",
                description = "통합 테스트용 쿠폰",
                discountRate = 15,
                totalQuantity = 1000,
                issuedQuantity = 0,
                startDate = LocalDateTime.now(),
                endDate = LocalDateTime.now().plusDays(30),
                validityDays = 30
            )
            val savedCoupon = couponRepository.save(testCoupon)
            testCouponId = savedCoupon.id!!
        }

        afterEach {
            // @DataJpaTest 자동 롤백으로 테스트 격리
        }

        describe("CouponService 통합 테스트 - Service와 Repository 통합") {
            context("쿠폰 발급") {
                it("사용자에게 쿠폰을 발급할 수 있다") {
                    // given
                    val userId = testUserId
                    val couponId = testCouponId
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
                    val userId = testUserId
                    val couponId = testCouponId

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
                    val userId = testUserId
                    val couponId = testCouponId
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
                    val userId = testUserId
                    val invalidCouponId = UUID.randomUUID()
                    val command = IssueCouponCommand(userId = userId)

                    // when & then
                    shouldThrow<CouponNotFoundException> {
                        couponService.issueCoupon(invalidCouponId, command)
                    }
                }
            }

            context("복합 시나리오 - 쿠폰 발급 및 조회 통합") {
                it("쿠폰 발급 후 목록 조회, 상세 조회를 연속으로 수행할 수 있다") {
                    // given
                    val userId = testUserId
                    val couponId = testCouponId

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
                    val userId1 = testUserId
                    val userId2 = testUser2Id
                    val couponId = testCouponId

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

            context("동시성 테스트 - 쿠폰 발급 (분산락 + 비관적 락 조합)") {
                it("100개의 쿠폰을 200명이 동시에 발급받을 때 정확히 100명만 성공한다 (분산락이 Redis에서 1차 방어)") {
                    // given - 쿠폰과 사용자를 별도 트랜잭션에서 생성하고 커밋
                    val couponId = executeInNewTransaction {
                        val coupon = Coupon(
                            name = "선착순 100명 쿠폰",
                            description = "동시성 테스트용 쿠폰",
                            discountRate = 10,
                            totalQuantity = 100,
                            issuedQuantity = 0,
                            startDate = LocalDateTime.now(),
                            endDate = LocalDateTime.now().plusDays(30),
                            validityDays = 30
                        )
                        val savedCoupon = couponRepository.save(coupon)
                        savedCoupon.id!!
                    }

                    // given - 200명의 사용자 생성
                    val userCount = 200
                    val userIds = mutableListOf<UUID>()

                    repeat(userCount) {
                        val userId = executeInNewTransaction {
                            val user = userService.createUser(CreateUserCommand(balance = 100000L))
                            user.id!!
                        }
                        userIds.add(userId)
                    }

                    // when - 200명이 동시에 쿠폰 발급 시도
                    val threadCount = 200
                    val executorService = Executors.newFixedThreadPool(threadCount)
                    val latch = CountDownLatch(threadCount)

                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    for (i in 0 until threadCount) {
                        val userId = userIds[i]
                        executorService.submit {
                            try {
                                latch.countDown()
                                latch.await() // 모든 스레드가 준비될 때까지 대기

                                // 각 스레드에서 새로운 트랜잭션으로 실행
                                executeInNewTransaction {
                                    val command = IssueCouponCommand(userId = userId)
                                    couponService.issueCoupon(couponId, command)
                                }

                                successCount.incrementAndGet()
                            } catch (e: CouponSoldOutException) {
                                // 쿠폰 품절 예외는 정상 동작
                                failCount.incrementAndGet()
                            } catch (e: CouponAlreadyIssuedException) {
                                // 중복 발급 예외도 정상 동작
                                failCount.incrementAndGet()
                            } catch (e: Exception) {
                                // 그 외 예외는 실패로 처리
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
                    println("=== 쿠폰 발급 결과 ===")
                    println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
                    successCount.get() shouldBe 100
                    failCount.get() shouldBe 100

                    // then - 쿠폰의 발급 수량 검증 (별도 트랜잭션에서 조회)
                    executeInNewTransaction {
                        val updatedCoupon = couponRepository.findById(couponId).get()
                        println("최종 발급 수량: ${updatedCoupon.issuedQuantity}")
                        updatedCoupon.issuedQuantity shouldBe 100

                        // then - 실제 발급된 UserCoupon 개수 검증
                        val issuedUserCoupons = userCouponRepository.findAll()
                        println("실제 UserCoupon 개수: ${issuedUserCoupons.size}")
                        issuedUserCoupons.filter { it.couponId == couponId }.size shouldBe 100
                    }
                }

                it("10개의 쿠폰을 50명이 동시에 발급받을 때 정확히 10명만 성공한다 (비관적 락이 DB에서 최종 방어)") {
                    // given - 쿠폰과 사용자를 별도 트랜잭션에서 생성하고 커밋
                    val couponId = executeInNewTransaction {
                        val coupon = Coupon(
                            name = "선착순 10명 쿠폰",
                            description = "동시성 테스트용 쿠폰 (소량)",
                            discountRate = 20,
                            totalQuantity = 10,
                            issuedQuantity = 0,
                            startDate = LocalDateTime.now(),
                            endDate = LocalDateTime.now().plusDays(30),
                            validityDays = 30
                        )
                        val savedCoupon = couponRepository.save(coupon)
                        savedCoupon.id!!
                    }

                    // given - 50명의 사용자 생성
                    val userCount = 50
                    val userIds = mutableListOf<UUID>()

                    repeat(userCount) {
                        val userId = executeInNewTransaction {
                            val user = userService.createUser(CreateUserCommand(balance = 100000L))
                            user.id!!
                        }
                        userIds.add(userId)
                    }

                    // when - 50명이 동시에 쿠폰 발급 시도
                    val threadCount = 50
                    val executorService = Executors.newFixedThreadPool(threadCount)
                    val latch = CountDownLatch(threadCount)

                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    for (i in 0 until threadCount) {
                        val userId = userIds[i]
                        executorService.submit {
                            try {
                                latch.countDown()
                                latch.await() // 모든 스레드가 준비될 때까지 대기

                                // 각 스레드에서 새로운 트랜잭션으로 실행
                                executeInNewTransaction {
                                    val command = IssueCouponCommand(userId = userId)
                                    couponService.issueCoupon(couponId, command)
                                }

                                successCount.incrementAndGet()
                            } catch (e: CouponSoldOutException) {
                                failCount.incrementAndGet()
                            } catch (e: CouponAlreadyIssuedException) {
                                failCount.incrementAndGet()
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
                    println("=== 쿠폰 발급 결과 ===")
                    println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
                    successCount.get() shouldBe 10
                    failCount.get() shouldBe 40

                    // then - 쿠폰의 발급 수량 검증 (별도 트랜잭션에서 조회)
                    executeInNewTransaction {
                        val updatedCoupon = couponRepository.findById(couponId).get()
                        println("최종 발급 수량: ${updatedCoupon.issuedQuantity}")
                        updatedCoupon.issuedQuantity shouldBe 10

                        // then - 실제 발급된 UserCoupon 개수 검증
                        val issuedUserCoupons = userCouponRepository.findAll()
                        println("실제 UserCoupon 개수: ${issuedUserCoupons.size}")
                        issuedUserCoupons.filter { it.couponId == couponId }.size shouldBe 10
                    }
                }

                it("분산락 + 비관적 락 조합 검증 - 대량 동시 요청에서 정확한 재고 관리") {
                    // given - 재고 50개인 쿠폰 생성
                    val couponId = executeInNewTransaction {
                        val coupon = Coupon(
                            name = "분산락+비관적락 테스트 쿠폰",
                            description = "분산락과 비관적 락의 조합 효과 검증",
                            discountRate = 15,
                            totalQuantity = 50,
                            issuedQuantity = 0,
                            startDate = LocalDateTime.now(),
                            endDate = LocalDateTime.now().plusDays(30),
                            validityDays = 30
                        )
                        val savedCoupon = couponRepository.save(coupon)
                        savedCoupon.id!!
                    }

                    // given - 100명의 사용자 생성
                    val userCount = 100
                    val userIds = mutableListOf<UUID>()

                    repeat(userCount) {
                        val userId = executeInNewTransaction {
                            val user = userService.createUser(CreateUserCommand(balance = 100000L))
                            user.id!!
                        }
                        userIds.add(userId)
                    }

                    // when - 100명이 동시에 쿠폰 발급 시도
                    // 분산락: Redis에서 1차 제어 (lock_key: coupon:issue:{couponId})
                    // 비관적 락: DB에서 2차 방어 (SELECT ... FOR UPDATE)
                    val threadCount = 100
                    val executorService = Executors.newFixedThreadPool(threadCount)
                    val latch = CountDownLatch(threadCount)

                    val successCount = AtomicInteger(0)
                    val soldOutCount = AtomicInteger(0)
                    val lockTimeoutCount = AtomicInteger(0)
                    val otherFailCount = AtomicInteger(0)

                    for (i in 0 until threadCount) {
                        val userId = userIds[i]
                        executorService.submit {
                            try {
                                latch.countDown()
                                latch.await() // 모든 스레드가 준비될 때까지 대기

                                // 각 스레드에서 새로운 트랜잭션으로 실행
                                executeInNewTransaction {
                                    val command = IssueCouponCommand(userId = userId)
                                    couponService.issueCoupon(couponId, command)
                                }

                                successCount.incrementAndGet()
                            } catch (e: CouponSoldOutException) {
                                // 비관적 락에서 감지된 재고 부족
                                soldOutCount.incrementAndGet()
                            } catch (e: LockAcquisitionFailedException) {
                                // 분산 락 획득 실패 (Redis 레벨에서 차단)
                                lockTimeoutCount.incrementAndGet()
                            } catch (e: CouponAlreadyIssuedException) {
                                // 중복 발급 방지
                                otherFailCount.incrementAndGet()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                otherFailCount.incrementAndGet()
                            }
                        }
                    }

                    executorService.shutdown()
                    while (!executorService.isTerminated) {
                        Thread.sleep(100)
                    }

                    // then - 결과 검증
                    // Assertion
                    val totalRequests = successCount.get() + soldOutCount.get() + lockTimeoutCount.get() + otherFailCount.get()
                    totalRequests shouldBe 100

                    // 정확히 50명만 성공, 50명은 실패해야 함
                    successCount.get() shouldBe 50

                    // 나머지 50명은 CouponSoldOutException 또는 LockAcquisitionFailedException
                    (soldOutCount.get() + lockTimeoutCount.get()) shouldBe 50

                    // 데이터 정합성 검증
                    executeInNewTransaction {
                        val updatedCoupon = couponRepository.findById(couponId).get()
                        updatedCoupon.issuedQuantity shouldBe 50

                        // 실제 발급된 UserCoupon 개수 검증
                        val issuedUserCoupons = userCouponRepository.findAll()
                        issuedUserCoupons.filter { it.couponId == couponId }.size shouldBe 50
                    }
                }
            }
        }
    }
}
