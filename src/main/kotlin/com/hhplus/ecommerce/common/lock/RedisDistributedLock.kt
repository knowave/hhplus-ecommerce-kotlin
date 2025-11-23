package com.hhplus.ecommerce.common.lock

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.UUID
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

    private val unlockScript = DefaultRedisScript<Long>().apply {
        setScriptText("""
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
        """)
        setResultType(Long::class.java)
    }

    /**
     * 분산 락 획득 시도
     *
     * @return 락 획득 성공 여부
     */
    fun tryLock(lockKey: String, waitTimeMs: Long = 3000, leaseTimeMs: Long = 5000): String? {
        val lockValue = "${UUID.randomUUID()}-${Thread.currentThread().id}"
        val startTime = System.currentTimeMillis()
        val endTime = startTime + waitTimeMs

        while (System.currentTimeMillis() < endTime) {
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMillis(leaseTimeMs)) ?: false

            if (acquired) {
                return lockValue
            }

            val remainingTime = endTime - System.currentTimeMillis()
            if (remainingTime <= 0) break

            val sleepTime = minOf(50L, remainingTime)

            try {
                TimeUnit.MILLISECONDS.sleep(sleepTime)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }

        return null
    }

    /**
     * 분산 락 해제
     */
    fun unlock(lockKey: String, lockValue: String): Boolean {
        return try {
            val result = redisTemplate.execute(
                unlockScript,
                listOf(lockKey),
                lockValue
            )
            result == 1L
        } catch (e: Exception) {
            return false
        }
    }

    fun unlockAfterCommit(lockKey: String, lockValue: String) {
        // 현재 트랜잭션이 활성화되어 있는지 확인
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 트랜잭션 커밋 후 실행될 콜백 등록
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        unlock(lockKey, lockValue)
                    }

                    override fun afterCompletion(status: Int) {
                        // 롤백 시에도 락 해제
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            unlock(lockKey, lockValue)
                        }
                    }
                }
            )
        } else {
            // 트랜잭션이 없으면 즉시 해제
            unlock(lockKey, lockValue)
        }
    }
}

/**
 * 락 획득 실패 예외
 */
class LockAcquisitionFailedException(message: String) : RuntimeException(message)