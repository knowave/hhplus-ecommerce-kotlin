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
     * 주기적으로 Redis Queue에서 요청을 꺼내 실제 발급 처리를 수행합니다.
     * 동기 메서드인 issueCoupon을 재사용하여 트랜잭션 및 락 처리를 위임합니다.
     */
    @Scheduled(fixedDelay = 500) // 0.5초마다 실행
    fun processCouponIssueQueue() {
        val queueKey = "coupon:issue:queue"
        val dlqKey = "coupon:issue:dlq"
        
        // 한 번 실행 시 최대 처리 개수
        val batchSize = 50 
        
        for (i in 0 until batchSize) {
            // 큐에서 하나씩 꺼냄 (LPOP)
            val message = redisTemplate.opsForList().leftPop(queueKey) ?: break
            
            try {
                val parts = message.split(":")
                if (parts.size != 2) {
                    logger.error("Invalid message format: $message")
                    // 형식이 잘못된 메시지는 DLQ로 보내거나 버림 (여기서는 DLQ로)
                    redisTemplate.opsForList().rightPush(dlqKey, message)
                    continue
                }
                
                val couponId = UUID.fromString(parts[0])
                val userId = UUID.fromString(parts[1])
                
                logger.info("Processing coupon issue request - couponId: $couponId, userId: $userId")
                
                // 실제 발급 처리 (이미 분산락/트랜잭션 적용됨)
                couponService.issueCoupon(couponId, IssueCouponCommand(userId))
                
                logger.info("Successfully issued coupon (Async) - couponId: $couponId, userId: $userId")
                
            } catch (e: Exception) {
                logger.error("Failed to process coupon issue request - message: $message, error: ${e.message}", e)
                
                // 예외 유형에 따른 처리
                when (e) {
                    is CouponAlreadyIssuedException -> {
                        // 이미 발급된 경우: 로그만 남기고 무시 (재시도 불필요)
                        logger.warn("Coupon already issued, ignoring request. message: $message")
                    }
                    is CouponOutOfStockException -> {
                        val exceptionMessage = "Coupon out of stock, ignoring request. message: $message"
                        logger.warn(exceptionMessage)
                    }
                    else -> {
                        // 그 외의 시스템 오류(DB 연결 실패, 락 획득 실패 등)는 DLQ로 이동하여 추후 재시도
                        logger.info("Moving failed request to DLQ. message: $message")
                        try {
                            redisTemplate.opsForList().rightPush(dlqKey, message)
                        } catch (dlqEx: Exception) {
                            logger.error("Failed to push to DLQ! message: $message", dlqEx)
                            // 최악의 경우: 로그에 남겨서 수동 처리 유도
                        }
                    }
                }
            }
        }
    }
}
