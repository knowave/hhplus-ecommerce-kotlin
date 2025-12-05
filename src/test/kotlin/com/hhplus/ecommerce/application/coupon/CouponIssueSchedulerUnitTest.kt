package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
import com.hhplus.ecommerce.application.coupon.dto.IssueCouponResult
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponOutOfStockException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import java.util.UUID

class CouponIssueSchedulerUnitTest : DescribeSpec({
    lateinit var redisTemplate: RedisTemplate<String, String>
    lateinit var couponService: CouponService
    lateinit var listOperations: ListOperations<String, String>
    lateinit var scheduler: CouponIssueScheduler

    beforeEach {
        redisTemplate = mockk()
        couponService = mockk()
        listOperations = mockk()

        every { redisTemplate.opsForList() } returns listOperations

        scheduler = CouponIssueScheduler(redisTemplate, couponService)
    }

    describe("CouponIssueScheduler 단위 테스트") {
        context("큐 처리 정상 케이스") {
            it("큐에서 메시지를 꺼내 쿠폰 발급을 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"
                val result = mockk<IssueCouponResult>()

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCoupon(couponId, any()) } returns result

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
            }

            it("큐에 여러 메시지가 있으면 순차적으로 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId1 = UUID.randomUUID()
                val userId2 = UUID.randomUUID()
                val userId3 = UUID.randomUUID()
                val message1 = "$couponId:$userId1"
                val message2 = "$couponId:$userId2"
                val message3 = "$couponId:$userId3"
                val result = mockk<IssueCouponResult>()

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message1, message2, message3, null)
                every { couponService.issueCoupon(couponId, any()) } returns result

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 3) { couponService.issueCoupon(couponId, any()) }
            }

            it("큐가 비어있으면 아무 처리도 하지 않는다") {
                // given
                every { listOperations.leftPop("coupon:issue:queue") } returns null

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 0) { couponService.issueCoupon(any(), any()) }
            }
        }

        context("예외 케이스 - DLQ 처리") {
            it("메시지 형식이 잘못된 경우 DLQ로 이동한다") {
                // given
                val invalidMessage = "invalid-message-format"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(invalidMessage, null)
                every { listOperations.rightPush("coupon:issue:dlq", invalidMessage) } returns 1L

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { listOperations.rightPush("coupon:issue:dlq", invalidMessage) }
                verify(exactly = 0) { couponService.issueCoupon(any(), any()) }
            }

            it("이미 발급된 쿠폰인 경우 로그만 남기고 DLQ로 이동하지 않는다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCoupon(couponId, any()) } throws CouponAlreadyIssuedException(userId, couponId)

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
                verify(exactly = 0) { listOperations.rightPush("coupon:issue:dlq", any()) }
            }

            it("재고 소진된 경우 로그만 남기고 DLQ로 이동하지 않는다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCoupon(couponId, any()) } throws CouponOutOfStockException("Sold out")

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
                verify(exactly = 0) { listOperations.rightPush("coupon:issue:dlq", any()) }
            }

            it("시스템 예외 발생 시 DLQ로 이동한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCoupon(couponId, any()) } throws RuntimeException("DB Connection failed")
                every { listOperations.rightPush("coupon:issue:dlq", message) } returns 1L

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
                verify(exactly = 1) { listOperations.rightPush("coupon:issue:dlq", message) }
            }
        }

        context("배치 처리") {
            it("한 번 실행 시 최대 50개까지 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val messages = (1..60).map { "$couponId:${UUID.randomUUID()}" }
                val result = mockk<IssueCouponResult>()

                // 50개 메시지 + null (51번째에서 종료되어야 하지만, 50개 제한으로 먼저 종료)
                every { listOperations.leftPop("coupon:issue:queue") } returnsMany (messages.take(50) + listOf(null))
                every { couponService.issueCoupon(couponId, any()) } returns result

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 50) { couponService.issueCoupon(couponId, any()) }
            }
        }
    }
})

