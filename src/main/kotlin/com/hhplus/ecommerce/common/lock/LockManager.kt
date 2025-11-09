package com.hhplus.ecommerce.common.lock

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 동시성 제어를 위한 Lock 관리자
 *
 * 도메인별로 독립적인 Lock을 관리하여 세밀한 동시성 제어를 제공합니다.
 * - User Lock: 사용자 잔액 동시성 제어
 * - Product Lock: 상품 재고 동시성 제어
 * - Coupon Lock: 쿠폰 발급/사용 동시성 제어
 *
 * Lock 순서를 통일하여 데드락을 방지합니다:
 * 1. Coupon Lock
 * 2. Product Lock
 * 3. User Lock
 */
@Component
class LockManager {

    private val logger = LoggerFactory.getLogger(LockManager::class.java)

    // 도메인별 Lock 저장소
    private val userLocks = ConcurrentHashMap<UUID, ReentrantLock>()
    private val productLocks = ConcurrentHashMap<UUID, ReentrantLock>()
    private val couponLocks = ConcurrentHashMap<UUID, ReentrantLock>()

    /**
     * 사용자 Lock을 획득하고 작업을 실행합니다.
     *
     * 주로 잔액 차감/환불 시 사용됩니다.
     */
    fun <T> executeWithUserLock(userId: UUID, action: () -> T): T {
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }
        logger.debug("Acquiring user lock for userId: {}", userId)
        lock.lock()
        try {
            logger.debug("User lock acquired for userId: {}", userId)
            return action()
        } finally {
            lock.unlock()
            logger.debug("User lock released for userId: {}", userId)
        }
    }

    /**
     * 상품 Lock을 획득하고 작업을 실행합니다.
     *
     * 주로 재고 차감/복원 시 사용됩니다.
     */
    fun <T> executeWithProductLock(productId: UUID, action: () -> T): T {
        val lock = productLocks.computeIfAbsent(productId) { ReentrantLock() }
        logger.debug("Acquiring product lock for productId: {}", productId)
        lock.lock()
        try {
            logger.debug("Product lock acquired for productId: {}", productId)
            return action()
        } finally {
            lock.unlock()
            logger.debug("Product lock released for productId: {}", productId)
        }
    }

    /**
     * 쿠폰 Lock을 획득하고 작업을 실행합니다.
     *
     * 주로 쿠폰 발급/사용 시 사용됩니다.
     */
    fun <T> executeWithCouponLock(couponId: UUID, action: () -> T): T {
        val lock = couponLocks.computeIfAbsent(couponId) { ReentrantLock() }
        logger.debug("Acquiring coupon lock for couponId: {}", couponId)
        lock.lock()
        try {
            logger.debug("Coupon lock acquired for couponId: {}", couponId)
            return action()
        } finally {
            lock.unlock()
            logger.debug("Coupon lock released for couponId: {}", couponId)
        }
    }

    /**
     * 여러 상품에 대한 Lock을 순차적으로 획득하고 작업을 실행합니다.
     *
     * 데드락 방지를 위해 productId를 정렬하여 항상 동일한 순서로 Lock을 획득합니다.
     */
    fun <T> executeWithProductLocks(productIds: List<UUID>, action: () -> T): T {
        // 데드락 방지: productId를 정렬하여 항상 동일한 순서로 Lock 획득
        val sortedProductIds = productIds.distinct().sorted()
        val locks = sortedProductIds.map { productId ->
            productLocks.computeIfAbsent(productId) { ReentrantLock() }
        }

        logger.debug("Acquiring product locks for productIds: {}", sortedProductIds)

        // 순차적으로 Lock 획득
        locks.forEach { it.lock() }
        try {
            logger.debug("All product locks acquired")
            return action()
        } finally {
            // 역순으로 Lock 해제 (LIFO)
            locks.reversed().forEach { it.unlock() }
            logger.debug("All product locks released")
        }
    }

    /**
     * 통합 Lock 실행 (Coupon → Product → User 순서)
     *
     * 데드락 방지를 위해 항상 동일한 순서로 Lock을 획득합니다.
     * 이 메서드는 주문+결제와 같은 복합 작업에서 사용됩니다.
     */
    fun <T> executeWithMultipleLocks(
        couponId: UUID? = null,
        productIds: List<UUID> = emptyList(),
        userId: UUID? = null,
        action: () -> T
    ): T {
        // 1. Coupon Lock (선택적)
        if (couponId != null) {
            return executeWithCouponLock(couponId) {
                executeLockChain(productIds, userId, action)
            }
        }

        // Coupon이 없으면 다음 단계로
        return executeLockChain(productIds, userId, action)
    }

    /**
     * Product → User Lock 순서로 실행 (내부 헬퍼 메서드)
     */
    private fun <T> executeLockChain(
        productIds: List<UUID>,
        userId: UUID?,
        action: () -> T
    ): T {
        // 2. Product Locks (선택적)
        if (productIds.isNotEmpty()) {
            return executeWithProductLocks(productIds) {
                executeUserLockOrAction(userId, action)
            }
        }

        // Product가 없으면 다음 단계로
        return executeUserLockOrAction(userId, action)
    }

    /**
     * User Lock 실행 (내부 헬퍼 메서드)
     */
    private fun <T> executeUserLockOrAction(userId: UUID?, action: () -> T): T {
        // 3. User Lock (선택적)
        if (userId != null) {
            return executeWithUserLock(userId, action)
        }

        // User Lock이 필요 없으면 바로 실행
        return action()
    }

    /**
     * Lock 통계 정보 조회 (모니터링용)
     */
    fun getLockStatistics(): LockStatistics {
        return LockStatistics(
            userLockCount = userLocks.size,
            productLockCount = productLocks.size,
            couponLockCount = couponLocks.size
        )
    }

    /**
     * Lock 정리 (메모리 관리용)
     *
     * 사용하지 않는 Lock 객체를 정리합니다.
     * 실제 운영 환경에서는 스케줄러로 주기적으로 호출하는 것을 권장합니다.
     */
    fun clearUnusedLocks() {
        userLocks.entries.removeIf { !it.value.hasQueuedThreads() && !it.value.isLocked }
        productLocks.entries.removeIf { !it.value.hasQueuedThreads() && !it.value.isLocked }
        couponLocks.entries.removeIf { !it.value.hasQueuedThreads() && !it.value.isLocked }
        logger.info("Unused locks cleared")
    }
}

/**
 * Lock 통계 정보
 */
data class LockStatistics(
    val userLockCount: Int,
    val productLockCount: Int,
    val couponLockCount: Int
)