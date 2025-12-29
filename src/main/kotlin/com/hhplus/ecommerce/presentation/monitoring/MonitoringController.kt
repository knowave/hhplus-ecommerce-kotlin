package com.hhplus.ecommerce.presentation.monitoring

import com.hhplus.ecommerce.common.lock.CircuitBreakerStatus
import com.hhplus.ecommerce.common.lock.DistributedLockService
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import com.hhplus.ecommerce.domain.kafka.repository.FailedMessageJpaRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

/**
 * 모니터링 API
 *
 * 시스템 상태 및 DLQ 모니터링 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/monitoring")
@Tag(name = "Monitoring", description = "시스템 모니터링 API")
class MonitoringController(
    private val failedMessageRepository: FailedMessageJpaRepository,
    private val distributedLockService: DistributedLockService?
) {

    @Operation(summary = "DLQ 메시지 요약", description = "처리 실패한 메시지 현황을 조회합니다")
    @GetMapping("/dlq/summary")
    fun getDlqSummary(): ResponseEntity<DlqSummaryResponse> {
        val messages = failedMessageRepository.findAll()

        val summary = DlqSummaryResponse(
            totalCount = messages.size,
            pendingCount = messages.count { it.status == FailedMessageStatus.PENDING },
            retryingCount = messages.count { it.status == FailedMessageStatus.RETRYING},
            failedCount = messages.count { it.status == FailedMessageStatus.FAILED },
            byTopic = messages.groupBy { it.topic }
                .mapValues { (_, msgs) -> 
                    TopicSummary(
                        count = msgs.size,
                        pending = msgs.count { it.status == FailedMessageStatus.PENDING },
                        failed = msgs.count { it.status == FailedMessageStatus.FAILED }
                    )
                },
            recentMessages = messages
                .sortedByDescending { it.createdAt }
                .take(10)
                .map { msg ->
                    DlqMessageSummary(
                        id = msg.id!!,
                        topic = msg.topic,
                        status = msg.status.name,
                        errorMessage = msg.errorMessage?.take(100),
                        retryCount = msg.retryCount,
                        createdAt = msg.createdAt.toString()
                    )
                },
            updatedAt = LocalDateTime.now().toString()
        )

        return ResponseEntity.ok(summary)
    }

    @Operation(summary = "DLQ 메시지 목록", description = "처리 실패한 메시지 목록을 조회합니다")
    @GetMapping("/dlq/messages")
    fun getDlqMessages(
        @RequestParam(required = false) topic: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<DlqMessagesResponse> {
        var messages = failedMessageRepository.findAll()

        // 필터 적용
        topic?.let { t -> messages = messages.filter { it.topic == t } }
        status?.let { s -> messages = messages.filter { it.status == FailedMessageStatus.FAILED } }

        // 페이징
        val total = messages.size
        val pagedMessages = messages
            .sortedByDescending { it.createdAt }
            .drop(page * size)
            .take(size)
            .map { msg ->
                DlqMessageDetail(
                    id = msg.id!!,
                    topic = msg.topic,
                    messageKey = msg.messageKey,
                    payload = msg.payload?.take(500),
                    status = msg.status.name,
                    errorMessage = msg.errorMessage,
                    stackTrace = msg.stackTrace?.take(1000),
                    retryCount = msg.retryCount,
                    partition = msg.partition,
                    offset = msg.offset,
                    createdAt = msg.createdAt.toString(),
                    updatedAt = msg.updatedAt.toString()
                )
            }

        return ResponseEntity.ok(
            DlqMessagesResponse(
                messages = pagedMessages,
                pagination = PaginationInfo(
                    page = page,
                    size = size,
                    total = total,
                    totalPages = (total + size - 1) / size
                )
            )
        )
    }

    @Operation(summary = "Circuit Breaker 상태", description = "분산락 Circuit Breaker 상태를 조회합니다")
    @GetMapping("/circuit-breaker")
    fun getCircuitBreakerStatus(): ResponseEntity<CircuitBreakerResponse> {
        val status = distributedLockService?.getStatus()
            ?: return ResponseEntity.ok(
                CircuitBreakerResponse(
                    enabled = false,
                    state = "DISABLED",
                    failureCount = 0,
                    lastFailureTime = null,
                    message = "분산락 서비스가 비활성화되어 있습니다."
                )
            )

        return ResponseEntity.ok(
            CircuitBreakerResponse(
                enabled = true,
                state = status.state.name,
                failureCount = status.failureCount,
                lastFailureTime = if (status.lastFailureTime > 0) {
                    java.time.Instant.ofEpochMilli(status.lastFailureTime).toString()
                } else null,
                message = when (status.state) {
                    DistributedLockService.CircuitState.CLOSED -> "Redis 분산락 정상 동작 중"
                    DistributedLockService.CircuitState.HALF_OPEN -> "Redis 복구 확인 중"
                    DistributedLockService.CircuitState.OPEN -> "Redis 장애 감지, DB Fallback 사용 중"
                }
            )
        )
    }

    @Operation(summary = "시스템 헬스 체크", description = "전체 시스템 상태를 조회합니다")
    @GetMapping("/health")
    fun getSystemHealth(): ResponseEntity<SystemHealthResponse> {
        val dlqCount = failedMessageRepository.count()
        val circuitState = distributedLockService?.getStatus()?.state?.name ?: "DISABLED"

        val healthStatus = when {
            circuitState == "OPEN" -> "DEGRADED"
            dlqCount > 100 -> "WARNING"
            else -> "HEALTHY"
        }

        return ResponseEntity.ok(
            SystemHealthResponse(
                status = healthStatus,
                components = mapOf(
                    "distributedLock" to ComponentStatus(
                        status = if (circuitState == "OPEN") "DOWN" else "UP",
                        details = mapOf("circuitState" to circuitState)
                    ),
                    "dlq" to ComponentStatus(
                        status = if (dlqCount > 100) "WARNING" else "UP",
                        details = mapOf("pendingMessages" to dlqCount)
                    )
                ),
                timestamp = LocalDateTime.now().toString()
            )
        )
    }
}

// ==================== DTOs ====================

data class DlqSummaryResponse(
    val totalCount: Int,
    val pendingCount: Int,
    val retryingCount: Int,
    val failedCount: Int,
    val byTopic: Map<String, TopicSummary>,
    val recentMessages: List<DlqMessageSummary>,
    val updatedAt: String
)

data class TopicSummary(
    val count: Int,
    val pending: Int,
    val failed: Int
)

data class DlqMessageSummary(
    val id: UUID,
    val topic: String,
    val status: String,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: String
)

data class DlqMessagesResponse(
    val messages: List<DlqMessageDetail>,
    val pagination: PaginationInfo
)

data class DlqMessageDetail(
    val id: UUID,
    val topic: String,
    val messageKey: String?,
    val payload: String?,
    val status: String,
    val errorMessage: String?,
    val stackTrace: String?,
    val retryCount: Int,
    val partition: Int,
    val offset: Long,
    val createdAt: String,
    val updatedAt: String
)

data class PaginationInfo(
    val page: Int,
    val size: Int,
    val total: Int,
    val totalPages: Int
)

data class CircuitBreakerResponse(
    val enabled: Boolean,
    val state: String,
    val failureCount: Int,
    val lastFailureTime: String?,
    val message: String
)

data class SystemHealthResponse(
    val status: String,
    val components: Map<String, ComponentStatus>,
    val timestamp: String
)

data class ComponentStatus(
    val status: String,
    val details: Map<String, Any>
)

