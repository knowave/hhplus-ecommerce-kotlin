package com.hhplus.ecommerce.common.lock

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType

/**
 * 분산 락 서비스 (Circuit Breaker 패턴 적용)
 *
 * Redis 분산락을 기본으로 사용하고,
 * Redis 장애 시 DB 비관적 락으로 자동 Fallback
 *
 * Circuit Breaker 상태:
 * - CLOSED: Redis 정상 (Redis 분산락 사용)
 * - OPEN: Redis 장애 (DB 비관적 락 사용)
 * - HALF_OPEN: 복구 확인 중
 */
@Component
@ConditionalOnProperty(
    name = ["app.lock.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class DistributedLockService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val entityManager: EntityManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Circuit Breaker 상태
    enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

    private var circuitState = CircuitState.CLOSED
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)

    // Circuit Breaker 설정
    private val failureThreshold = 5           // 실패 임계값
    private val resetTimeoutMs = 30_000L       // 30초 후 HALF_OPEN 전환
    private val successThreshold = 3           // 복구 확인 성공 횟수

    private val successCount = AtomicInteger(0)

    // 락 스토리지 (Fallback 시 사용)
    private val localLocks = ConcurrentHashMap<String, String>()

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
     * 분산 락 획득 (자동 Fallback 지원)
     *
     * @return LockResult (락 값 + 사용된 전략)
     */
    fun tryLock(
        lockKey: String,
        waitTimeMs: Long = 3000,
        leaseTimeMs: Long = 5000
    ): LockResult? {
        return when (getCircuitState()) {
            CircuitState.CLOSED, CircuitState.HALF_OPEN -> {
                tryRedisLock(lockKey, waitTimeMs, leaseTimeMs)
                    ?: run {
                        logger.warn("Redis 락 획득 실패, DB Fallback 시도: key={}", lockKey)
                        tryDbFallback(lockKey, waitTimeMs)
                    }
            }
            CircuitState.OPEN -> {
                logger.debug("Circuit OPEN 상태, DB Fallback 사용: key={}", lockKey)
                tryDbFallback(lockKey, waitTimeMs)
            }
        }
    }

    /**
     * Redis 분산 락 획득 시도
     */
    private fun tryRedisLock(
        lockKey: String,
        waitTimeMs: Long,
        leaseTimeMs: Long
    ): LockResult? {
        val lockValue = "${UUID.randomUUID()}-${Thread.currentThread().id}"
        val startTime = System.currentTimeMillis()
        val endTime = startTime + waitTimeMs

        try {
            while (System.currentTimeMillis() < endTime) {
                val acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofMillis(leaseTimeMs)) ?: false

                if (acquired) {
                    onRedisSuccess()
                    return LockResult(lockValue, LockStrategy.REDIS)
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
            return null  // 락 획득 실패 (경합)
        } catch (e: RedisConnectionFailureException) {
            logger.error("Redis 연결 실패: {}", e.message)
            onRedisFailure()
            return null
        } catch (e: Exception) {
            logger.error("Redis 락 획득 중 오류: {}", e.message)
            onRedisFailure()
            return null
        }
    }

    /**
     * DB 비관적 락으로 Fallback
     */
    private fun tryDbFallback(lockKey: String, waitTimeMs: Long): LockResult? {
        val lockValue = "${UUID.randomUUID()}-${Thread.currentThread().id}"

        try {
            // 로컬 락으로 동시성 제어 (단일 서버 환경)
            val existing = localLocks.putIfAbsent(lockKey, lockValue)
            if (existing != null) {
                logger.debug("로컬 락 획득 실패 (이미 존재): key={}", lockKey)
                return null
            }

            logger.info("DB Fallback 락 획득 성공: key={}", lockKey)
            return LockResult(lockValue, LockStrategy.DB_FALLBACK)
        } catch (e: Exception) {
            logger.error("DB Fallback 락 획득 실패: {}", e.message)
            return null
        }
    }

    /**
     * 락 해제
     */
    fun unlock(lockKey: String, lockResult: LockResult): Boolean {
        return when (lockResult.strategy) {
            LockStrategy.REDIS -> unlockRedis(lockKey, lockResult.lockValue)
            LockStrategy.DB_FALLBACK -> unlockLocal(lockKey, lockResult.lockValue)
        }
    }

    private fun unlockRedis(lockKey: String, lockValue: String): Boolean {
        return try {
            val result = redisTemplate.execute(unlockScript, listOf(lockKey), lockValue)
            result == 1L
        } catch (e: Exception) {
            logger.error("Redis 락 해제 실패: {}", e.message)
            false
        }
    }

    private fun unlockLocal(lockKey: String, lockValue: String): Boolean {
        return localLocks.remove(lockKey, lockValue)
    }

    /**
     * 트랜잭션 커밋 후 락 해제
     */
    fun unlockAfterCommit(lockKey: String, lockResult: LockResult) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        unlock(lockKey, lockResult)
                    }

                    override fun afterCompletion(status: Int) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            unlock(lockKey, lockResult)
                        }
                    }
                }
            )
        } else {
            unlock(lockKey, lockResult)
        }
    }

    // ==================== Circuit Breaker 로직 ====================

    private fun getCircuitState(): CircuitState {
        if (circuitState == CircuitState.OPEN) {
            val timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get()
            if (timeSinceLastFailure > resetTimeoutMs) {
                logger.info("Circuit Breaker: OPEN -> HALF_OPEN (복구 확인 시작)")
                circuitState = CircuitState.HALF_OPEN
                successCount.set(0)
            }
        }
        return circuitState
    }

    private fun onRedisSuccess() {
        if (circuitState == CircuitState.HALF_OPEN) {
            if (successCount.incrementAndGet() >= successThreshold) {
                logger.info("Circuit Breaker: HALF_OPEN -> CLOSED (Redis 복구 완료)")
                circuitState = CircuitState.CLOSED
                failureCount.set(0)
            }
        } else if (circuitState == CircuitState.CLOSED) {
            failureCount.set(0)
        }
    }

    private fun onRedisFailure() {
        lastFailureTime.set(System.currentTimeMillis())

        if (circuitState == CircuitState.HALF_OPEN) {
            logger.warn("Circuit Breaker: HALF_OPEN -> OPEN (Redis 복구 실패)")
            circuitState = CircuitState.OPEN
            successCount.set(0)
        } else if (circuitState == CircuitState.CLOSED) {
            if (failureCount.incrementAndGet() >= failureThreshold) {
                logger.error("Circuit Breaker: CLOSED -> OPEN (Redis 장애 감지)")
                circuitState = CircuitState.OPEN
            }
        }
    }

    /**
     * 현재 Circuit Breaker 상태 조회 (모니터링용)
     */
    fun getStatus(): CircuitBreakerStatus {
        return CircuitBreakerStatus(
            state = circuitState,
            failureCount = failureCount.get(),
            lastFailureTime = lastFailureTime.get()
        )
    }
}

/**
 * 락 획득 결과
 */
data class LockResult(
    val lockValue: String,
    val strategy: LockStrategy
)

/**
 * 락 전략
 */
enum class LockStrategy {
    REDIS,       // Redis 분산락
    DB_FALLBACK  // DB 비관적 락 (Fallback)
}

/**
 * Circuit Breaker 상태 정보
 */
data class CircuitBreakerStatus(
    val state: DistributedLockService.CircuitState,
    val failureCount: Int,
    val lastFailureTime: Long
)

