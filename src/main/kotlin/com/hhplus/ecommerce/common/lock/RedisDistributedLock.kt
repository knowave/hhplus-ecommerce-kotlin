package com.hhplus.ecommerce.common.lock

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 분산 락 구현
 *
 * 선착순 쿠폰 발급과 같은 고동시성 시나리오에서 사용:
 * - 멀티 서버 환경에서 동시성 제어
 * - 빠른 락 획득/해제 (메모리 기반)
 * - DB 부하 감소 (Redis 레벨에서 제어)
 *
 */
@Component
class RedisDistributedLock(
    private val redisTemplate: RedisTemplate<String, String>
) {

    /**
     * 분산 락 획득 시도
     *
     * @param lockKey 락 키 (예: "coupon:issue:${couponId}")
     * @param waitTimeMs 락 획득 대기 시간 (밀리초)
     * @param leaseTimeMs 락 보유 시간 (밀리초) - 락 획득 후 자동 해제되는 시간
     * @return 락 획득 성공 여부
     */
    fun tryLock(
        lockKey: String,
        waitTimeMs: Long = 3000,
        leaseTimeMs: Long = 5000
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val lockValue = Thread.currentThread().id.toString()

        while (System.currentTimeMillis() - startTime < waitTimeMs) {
            // SETNX (SET if Not exists) 방식으로 락 획득 시도
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMillis(leaseTimeMs)) ?: false

            if (acquired) {
                return true
            }

            // 락 획득 실패 시 짧은 대기 후 재시도
            try {
                TimeUnit.MILLISECONDS.sleep(50)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        return false
    }

    /**
     * 분산 락 해제
     *
     * @param lockKey 락 키
     */
    fun unlock(lockKey: String) {
        redisTemplate.delete(lockKey)
    }

    /**
     * 락 획득 후 작업 실행 (고차 함수)
     *
     * @param lockKey 락 키
     * @param waitTimeMs 락 획득 대기 시간 (밀리초)
     * @param leaseTimeMs 락 보유 시간 (밀리초)
     * @param action 락 획득 후 실행할 작업
     * @return 작업 실행 결과
     * @throws LockAcquisitionFailedException 락 획득 실패 시
     */
    fun <T> executeWithLock(
        lockKey: String,
        waitTimeMs: Long = 3000,
        leaseTimeMs: Long = 5000,
        action: () -> T
    ): T {
        val acquired = tryLock(lockKey, waitTimeMs, leaseTimeMs)

        if (!acquired) {
            throw LockAcquisitionFailedException("Failed to acquire lock for key: $lockKey")
        }

        return try {
            action()
        } finally {
            unlock(lockKey)
        }
    }
}

/**
 * 락 획득 실패 예외
 */
class LockAcquisitionFailedException(message: String) : RuntimeException(message)