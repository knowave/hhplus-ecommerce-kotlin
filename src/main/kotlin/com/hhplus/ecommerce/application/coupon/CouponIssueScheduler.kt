package com.hhplus.ecommerce.application.coupon

import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
import com.hhplus.ecommerce.common.exception.BaseException
import com.hhplus.ecommerce.common.exception.CouponAlreadyIssuedException
import com.hhplus.ecommerce.common.exception.CouponOutOfStockException
import com.hhplus.ecommerce.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CouponIssueScheduler(
    private val redisTemplate: RedisTemplate<String, String>,
    private val couponService: CouponService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 대기열 처리 (Consumer)
     *
     * 주기적으로 Redis Queue에서 요청을 배치로 꺼내 실제 발급 처리를 수행합니다.
     * 배치 처리를 통해 DB 호출을 최소화하고 성능을 향상시킵니다.
     */
    @Scheduled(fixedDelay = 2000) // 2초마다 실행 (스케줄러 밀림 방지)
    fun processCouponIssueQueue() {
        val queueKey = "coupon:issue:queue"
        val dlqKey = "coupon:issue:dlq"

        // 한 번 실행 시 최대 처리 개수 (배치 크기 축소)
        val batchSize = 20

        // Redis에서 메시지를 배치로 가져오기
        val messages = mutableListOf<String>()
        val invalidMessages = mutableListOf<String>()
        val validRequests = mutableListOf<Pair<UUID, UUID>>() // couponId, userId

        for (i in 0 until batchSize) {
            val message = redisTemplate.opsForList().leftPop(queueKey) ?: break
            messages.add(message)

            // 메시지 파싱
            try {
                val parts = message.split(":")
                if (parts.size != 2) {
                    logger.error("Invalid message format: $message")
                    invalidMessages.add(message)
                    continue
                }

                val couponId = UUID.fromString(parts[0])
                val userId = UUID.fromString(parts[1])
                validRequests.add(Pair(couponId, userId))
            } catch (e: Exception) {
                logger.error("Failed to parse message: $message", e)
                invalidMessages.add(message)
            }
        }

        // 잘못된 메시지는 DLQ로 이동
        if (invalidMessages.isNotEmpty()) {
            try {
                invalidMessages.forEach { msg ->
                    redisTemplate.opsForList().rightPush(dlqKey, msg)
                }
                logger.warn("Moved ${invalidMessages.size} invalid messages to DLQ")
            } catch (e: Exception) {
                logger.error("Failed to move invalid messages to DLQ", e)
            }
        }

        // 유효한 요청을 배치로 처리
        if (validRequests.isNotEmpty()) {
            try {
                logger.info("Processing ${validRequests.size} coupon issue requests in batch")

                val successCount = couponService.issueCouponBatch(validRequests)

                logger.info("Successfully issued $successCount coupons out of ${validRequests.size} requests")

            } catch (e: Exception) {
                logger.error("Failed to process coupon batch", e)

                // 배치 처리 실패 시, 개별 처리로 폴백
                logger.info("Falling back to individual processing")
                fallbackToIndividualProcessing(validRequests, dlqKey)
            }
        }
    }

    /**
     * 배치 처리 실패 시 개별 처리로 폴백
     */
    private fun fallbackToIndividualProcessing(requests: List<Pair<UUID, UUID>>, dlqKey: String) {
        requests.forEach { (couponId, userId) ->
            try {
                couponService.issueCoupon(couponId, IssueCouponCommand(userId))
                logger.info("Successfully issued coupon - couponId: $couponId, userId: $userId")
            } catch (e: Exception) {
                logger.error("Failed to issue coupon - couponId: $couponId, userId: $userId, error: ${e.message}", e)

                // 예외 유형에 따른 처리
                when (e) {
                    is CouponAlreadyIssuedException -> {
                        logger.warn("Coupon already issued, ignoring request. couponId: $couponId, userId: $userId")
                    }
                    is CouponOutOfStockException -> {
                        logger.warn("Coupon out of stock, ignoring request. couponId: $couponId")
                    }
                    else -> {
                        // 시스템 오류는 DLQ로 이동
                        val message = "$couponId:$userId"
                        try {
                            redisTemplate.opsForList().rightPush(dlqKey, message)
                            logger.info("Moved failed request to DLQ: $message")
                        } catch (dlqEx: Exception) {
                            logger.error("Failed to push to DLQ! message: $message", dlqEx)
                        }
                    }
                }
            }
        }
    }
}
