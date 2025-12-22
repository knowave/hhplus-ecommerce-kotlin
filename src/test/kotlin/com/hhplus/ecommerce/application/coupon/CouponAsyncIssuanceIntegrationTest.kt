package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
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
@EmbeddedKafka(
    partitions = 1,
    topics = ["order-created", "payment-completed", "coupon-issued"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"]
)
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-group"
    ]
)
@Import(
    com.hhplus.ecommerce.config.EmbeddedRedisConfig::class,
    com.hhplus.ecommerce.config.TestRedisConfig::class
)
class CouponAsyncIssuanceIntegrationTest(
    private val couponService: CouponService,
    private val userService: UserService,
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val couponIssueScheduler: CouponIssueScheduler,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

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
            // Redis 데이터 초기화
            redisTemplate.keys("coupon:*")?.forEach { key ->
                redisTemplate.delete(key)
            }
        }

        describe("쿠폰 비동기 발급 통합 테스트") {
            context("비동기 발급 요청") {
                it("requestCouponIssuance 호출 시 Redis Queue에 메시지가 적재된다") {
                    // given
                    val userId = executeInNewTransaction {
                        val user = userService.createUser(CreateUserCommand(balance = 100000L))
                        user.id!!
                    }

                    val couponId = executeInNewTransaction {
                        val coupon = Coupon(
                            name = "비동기 발급 테스트 쿠폰",
                            description = "통합 테스트",
                            discountRate = 10,
                            totalQuantity = 100,
                            issuedQuantity = 0,
                            startDate = LocalDateTime.now(),
                            endDate = LocalDateTime.now().plusDays(30),
                            validityDays = 30
                        )
                        couponRepository.save(coupon).id!!
                    }

                    // when
                    val command = IssueCouponCommand(userId = userId)
                    val result = couponService.requestCouponIssuance(couponId, command)

                    // then
                    result.status shouldBe "QUEUED"
                    result.couponId shouldBe couponId
                    result.userId shouldBe userId

                    // Redis Queue에 메시지가 적재되었는지 확인
                    val queueSize = redisTemplate.opsForList().size("coupon:issue:queue")
                    queueSize shouldNotBe null
                    (queueSize!! >= 1) shouldBe true
                }

                it("Scheduler가 Queue에서 메시지를 꺼내 처리한다") {
                    // given
                    val userId = executeInNewTransaction {
                        val user = userService.createUser(CreateUserCommand(balance = 100000L))
                        user.id!!
                    }

                    val couponId = executeInNewTransaction {
                        val coupon = Coupon(
                            name = "스케줄러 테스트 쿠폰",
                            description = "통합 테스트",
                            discountRate = 15,
                            totalQuantity = 100,
                            issuedQuantity = 0,
                            startDate = LocalDateTime.now(),
                            endDate = LocalDateTime.now().plusDays(30),
                            validityDays = 30
                        )
                        couponRepository.save(coupon).id!!
                    }

                    // when - 비동기 발급 요청
                    val command = IssueCouponCommand(userId = userId)
                    couponService.requestCouponIssuance(couponId, command)

                    // then - Queue에 메시지가 적재되었는지 확인
                    val queueSizeBefore = redisTemplate.opsForList().size("coupon:issue:queue")
                    (queueSizeBefore!! >= 1) shouldBe true

                    // when - 스케줄러 실행
                    couponIssueScheduler.processCouponIssueQueue()

                    // then - Queue에서 메시지가 처리되었는지 확인 (Queue 비워짐)
                    val queueSizeAfter = redisTemplate.opsForList().size("coupon:issue:queue")
                    queueSizeAfter shouldBe 0L
                    
                    // 참고: 실제 UserCoupon 생성 여부는 CouponServiceIntegrationTest에서 검증됨
                    // 스케줄러 동작 로직은 CouponIssueSchedulerUnitTest에서 검증됨
                }
            }

            context("동시성 테스트 - Redis 기반 재고 관리") {
                it("50개 쿠폰에 100명이 동시에 비동기 요청 시 Redis에서 선착순 50명만 Queue에 적재된다") {
                    // given - 쿠폰 생성
                    val couponId = executeInNewTransaction {
                        val coupon = Coupon(
                            name = "선착순 50명 비동기 쿠폰",
                            description = "Redis Queue 동시성 테스트",
                            discountRate = 20,
                            totalQuantity = 50,
                            issuedQuantity = 0,
                            startDate = LocalDateTime.now(),
                            endDate = LocalDateTime.now().plusDays(30),
                            validityDays = 30
                        )
                        couponRepository.save(coupon).id!!
                    }

                    // given - 100명의 사용자 생성
                    val userIds = mutableListOf<UUID>()
                    repeat(100) {
                        val userId = executeInNewTransaction {
                            val user = userService.createUser(CreateUserCommand(balance = 100000L))
                            user.id!!
                        }
                        userIds.add(userId)
                    }

                    // when - 100명이 동시에 비동기 발급 요청
                    val threadCount = 100
                    val executorService = Executors.newFixedThreadPool(threadCount)
                    val latch = CountDownLatch(threadCount)

                    val requestSuccessCount = AtomicInteger(0)
                    val requestFailCount = AtomicInteger(0)

                    for (i in 0 until threadCount) {
                        val userId = userIds[i]
                        executorService.submit {
                            try {
                                latch.countDown()
                                latch.await()

                                val command = IssueCouponCommand(userId = userId)
                                couponService.requestCouponIssuance(couponId, command)
                                requestSuccessCount.incrementAndGet()
                            } catch (e: Exception) {
                                // CouponOutOfStockException 등
                                requestFailCount.incrementAndGet()
                            }
                        }
                    }

                    executorService.shutdown()
                    while (!executorService.isTerminated) {
                        Thread.sleep(100)
                    }

                    // then - Redis에서 재고 체크하므로 50명만 성공해야 함
                    requestSuccessCount.get() shouldBe 50
                    requestFailCount.get() shouldBe 50

                    // then - Queue에 50개의 메시지만 적재되었는지 확인
                    val queueSize = redisTemplate.opsForList().size("coupon:issue:queue")
                    queueSize shouldBe 50L

                    // then - 중복 방지 Set에 50명만 등록되었는지 확인
                    val setSize = redisTemplate.opsForSet().size("coupon:$couponId:users")
                    setSize shouldBe 50L
                    
                    // 참고: 실제 스케줄러 처리 및 DB 저장은 별도 테스트에서 검증
                    // 스케줄러 동작은 CouponIssueSchedulerUnitTest에서 검증됨
                }
            }
        }
    }
}

