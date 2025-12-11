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

    describe("CouponIssueScheduler 단위 테스트 - 배치 처리") {
        context("큐 처리 정상 케이스") {
            it("큐에서 메시지를 꺼내 배치로 쿠폰 발급을 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCouponBatch(any()) } returns 1

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) {
                    couponService.issueCouponBatch(match { requests ->
                        requests.size == 1 && requests[0].first == couponId && requests[0].second == userId
                    })
                }
            }

            it("큐에 여러 메시지가 있으면 배치로 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId1 = UUID.randomUUID()
                val userId2 = UUID.randomUUID()
                val userId3 = UUID.randomUUID()
                val message1 = "$couponId:$userId1"
                val message2 = "$couponId:$userId2"
                val message3 = "$couponId:$userId3"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message1, message2, message3, null)
                every { couponService.issueCouponBatch(any()) } returns 3

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) {
                    couponService.issueCouponBatch(match { requests ->
                        requests.size == 3
                    })
                }
            }

            it("큐가 비어있으면 아무 처리도 하지 않는다") {
                // given
                every { listOperations.leftPop("coupon:issue:queue") } returns null

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 0) { couponService.issueCouponBatch(any()) }
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
                verify(exactly = 0) { couponService.issueCouponBatch(any()) }
                verify(exactly = 0) { couponService.issueCoupon(any(), any()) }
            }

            it("배치 처리 실패 시 fallback으로 개별 처리하고, 이미 발급된 쿠폰은 DLQ로 이동하지 않는다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCouponBatch(any()) } throws RuntimeException("Batch failed")
                every { couponService.issueCoupon(couponId, any()) } throws CouponAlreadyIssuedException(userId, couponId)

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCouponBatch(any()) }
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
                verify(exactly = 0) { listOperations.rightPush("coupon:issue:dlq", any()) }
            }

            it("배치 처리 실패 시 fallback으로 개별 처리하고, 재고 소진은 DLQ로 이동하지 않는다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCouponBatch(any()) } throws RuntimeException("Batch failed")
                every { couponService.issueCoupon(couponId, any()) } throws CouponOutOfStockException("Sold out")

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCouponBatch(any()) }
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
                verify(exactly = 0) { listOperations.rightPush("coupon:issue:dlq", any()) }
            }

            it("배치 처리 실패 후 fallback에서 시스템 예외 발생 시 DLQ로 이동한다") {
                // given
                val couponId = UUID.randomUUID()
                val userId = UUID.randomUUID()
                val message = "$couponId:$userId"

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany listOf(message, null)
                every { couponService.issueCouponBatch(any()) } throws RuntimeException("Batch failed")
                every { couponService.issueCoupon(couponId, any()) } throws RuntimeException("DB Connection failed")
                every { listOperations.rightPush("coupon:issue:dlq", message) } returns 1L

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) { couponService.issueCouponBatch(any()) }
                verify(exactly = 1) { couponService.issueCoupon(couponId, any()) }
                verify(exactly = 1) { listOperations.rightPush("coupon:issue:dlq", message) }
            }
        }

        context("배치 크기 제한") {
            it("한 번 실행 시 최대 20개까지 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val messages = (1..30).map { "$couponId:${UUID.randomUUID()}" }

                // 30개의 메시지가 있지만, 20개만 꺼내고 종료
                every { listOperations.leftPop("coupon:issue:queue") } returnsMany messages.take(20)
                every { couponService.issueCouponBatch(any()) } returns 20

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) {
                    couponService.issueCouponBatch(match { requests ->
                        requests.size == 20
                    })
                }
            }

            it("메시지가 20개 미만이면 있는 만큼만 처리한다") {
                // given
                val couponId = UUID.randomUUID()
                val messages = (1..5).map { "$couponId:${UUID.randomUUID()}" }

                every { listOperations.leftPop("coupon:issue:queue") } returnsMany (messages + listOf(null))
                every { couponService.issueCouponBatch(any()) } returns 5

                // when
                scheduler.processCouponIssueQueue()

                // then
                verify(exactly = 1) {
                    couponService.issueCouponBatch(match { requests ->
                        requests.size == 5
                    })
                }
            }
        }
    }
})

