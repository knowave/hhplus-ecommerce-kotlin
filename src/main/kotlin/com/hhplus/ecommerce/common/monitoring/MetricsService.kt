package com.hhplus.ecommerce.common.monitoring

import com.hhplus.ecommerce.common.lock.DistributedLockService
import com.hhplus.ecommerce.common.lock.LockStrategy
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong
import jakarta.annotation.PostConstruct

/**
 * 커스텀 메트릭 수집 서비스
 *
 * Prometheus/Grafana를 통해 모니터링할 수 있는 비즈니스 메트릭을 제공합니다.
 *
 * 수집 메트릭:
 * - 주문/결제 처리 수
 * - 쿠폰 발급 수
 * - 분산락 획득 성공/실패 수
 * - DLQ 메시지 수
 * - Circuit Breaker 상태
 */
@Service
class MetricsService(
    private val meterRegistry: MeterRegistry,
    private val distributedLockService: DistributedLockService?
) {

    // 카운터 메트릭
    private lateinit var orderCreatedCounter: Counter
    private lateinit var orderFailedCounter: Counter
    private lateinit var paymentSuccessCounter: Counter
    private lateinit var paymentFailedCounter: Counter
    private lateinit var couponIssuedCounter: Counter
    private lateinit var couponFailedCounter: Counter

    // 분산락 메트릭
    private lateinit var lockAcquiredRedisCounter: Counter
    private lateinit var lockAcquiredFallbackCounter: Counter
    private lateinit var lockFailedCounter: Counter

    // DLQ 메트릭
    private lateinit var dlqOrderCreatedCounter: Counter
    private lateinit var dlqPaymentCompletedCounter: Counter
    private lateinit var dlqCouponIssuedCounter: Counter
    private val dlqTotalCount = AtomicLong(0)

    // 타이머 메트릭
    private lateinit var orderProcessingTimer: Timer
    private lateinit var paymentProcessingTimer: Timer
    private lateinit var couponIssueTimer: Timer

    @PostConstruct
    fun init() {
        // 주문 메트릭
        orderCreatedCounter = Counter.builder("order.created.total")
            .description("Total number of orders created")
            .tag("status", "success")
            .register(meterRegistry)

        orderFailedCounter = Counter.builder("order.created.total")
            .description("Total number of failed orders")
            .tag("status", "failed")
            .register(meterRegistry)

        // 결제 메트릭
        paymentSuccessCounter = Counter.builder("payment.processed.total")
            .description("Total number of successful payments")
            .tag("status", "success")
            .register(meterRegistry)

        paymentFailedCounter = Counter.builder("payment.processed.total")
            .description("Total number of failed payments")
            .tag("status", "failed")
            .register(meterRegistry)

        // 쿠폰 메트릭
        couponIssuedCounter = Counter.builder("coupon.issued.total")
            .description("Total number of coupons issued")
            .tag("status", "success")
            .register(meterRegistry)

        couponFailedCounter = Counter.builder("coupon.issued.total")
            .description("Total number of failed coupon issues")
            .tag("status", "failed")
            .register(meterRegistry)

        // 분산락 메트릭
        lockAcquiredRedisCounter = Counter.builder("distributed.lock.acquired")
            .description("Lock acquired count")
            .tag("strategy", "redis")
            .register(meterRegistry)

        lockAcquiredFallbackCounter = Counter.builder("distributed.lock.acquired")
            .description("Lock acquired count via fallback")
            .tag("strategy", "db_fallback")
            .register(meterRegistry)

        lockFailedCounter = Counter.builder("distributed.lock.failed")
            .description("Lock acquisition failed count")
            .register(meterRegistry)

        // DLQ 메트릭
        dlqOrderCreatedCounter = Counter.builder("dlq.messages.total")
            .description("DLQ messages count")
            .tag("topic", "order-created")
            .register(meterRegistry)

        dlqPaymentCompletedCounter = Counter.builder("dlq.messages.total")
            .description("DLQ messages count")
            .tag("topic", "payment-completed")
            .register(meterRegistry)

        dlqCouponIssuedCounter = Counter.builder("dlq.messages.total")
            .description("DLQ messages count")
            .tag("topic", "coupon-issued")
            .register(meterRegistry)

        // DLQ 총합 게이지
        Gauge.builder("dlq.messages.pending", dlqTotalCount) { it.get().toDouble() }
            .description("Total pending DLQ messages")
            .register(meterRegistry)

        // Circuit Breaker 상태 게이지
        distributedLockService?.let { lockService ->
            Gauge.builder("circuit.breaker.state") {
                when (lockService.getStatus().state) {
                    DistributedLockService.CircuitState.CLOSED -> 0.0
                    DistributedLockService.CircuitState.HALF_OPEN -> 0.5
                    DistributedLockService.CircuitState.OPEN -> 1.0
                }
            }
                .description("Circuit breaker state (0=CLOSED, 0.5=HALF_OPEN, 1=OPEN)")
                .register(meterRegistry)

            Gauge.builder("circuit.breaker.failure.count") {
                lockService.getStatus().failureCount.toDouble()
            }
                .description("Circuit breaker failure count")
                .register(meterRegistry)
        }

        // 타이머
        orderProcessingTimer = Timer.builder("order.processing.time")
            .description("Order processing time")
            .register(meterRegistry)

        paymentProcessingTimer = Timer.builder("payment.processing.time")
            .description("Payment processing time")
            .register(meterRegistry)

        couponIssueTimer = Timer.builder("coupon.issue.time")
            .description("Coupon issue processing time")
            .register(meterRegistry)
    }

    // ==================== 주문 메트릭 ====================

    fun recordOrderCreated() = orderCreatedCounter.increment()
    fun recordOrderFailed() = orderFailedCounter.increment()

    fun <T> timeOrderProcessing(block: () -> T): T {
        return orderProcessingTimer.recordCallable(block)!!
    }

    // ==================== 결제 메트릭 ====================

    fun recordPaymentSuccess() = paymentSuccessCounter.increment()
    fun recordPaymentFailed() = paymentFailedCounter.increment()

    fun <T> timePaymentProcessing(block: () -> T): T {
        return paymentProcessingTimer.recordCallable(block)!!
    }

    // ==================== 쿠폰 메트릭 ====================

    fun recordCouponIssued() = couponIssuedCounter.increment()
    fun recordCouponFailed() = couponFailedCounter.increment()

    fun <T> timeCouponIssue(block: () -> T): T {
        return couponIssueTimer.recordCallable(block)!!
    }

    // ==================== 분산락 메트릭 ====================

    fun recordLockAcquired(strategy: LockStrategy) {
        when (strategy) {
            LockStrategy.REDIS -> lockAcquiredRedisCounter.increment()
            LockStrategy.DB_FALLBACK -> lockAcquiredFallbackCounter.increment()
        }
    }

    fun recordLockFailed() = lockFailedCounter.increment()

    // ==================== DLQ 메트릭 ====================

    fun recordDlqMessage(topic: String) {
        dlqTotalCount.incrementAndGet()
        when {
            topic.contains("order-created") -> dlqOrderCreatedCounter.increment()
            topic.contains("payment-completed") -> dlqPaymentCompletedCounter.increment()
            topic.contains("coupon-issued") -> dlqCouponIssuedCounter.increment()
        }
    }

    fun recordDlqMessageProcessed() {
        val current = dlqTotalCount.get()
        if (current > 0) {
            dlqTotalCount.decrementAndGet()
        }
    }
}

