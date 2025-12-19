package com.hhplus.ecommerce.presentation.kafka

import com.hhplus.ecommerce.application.kafka.FailedMessageService
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import com.hhplus.ecommerce.presentation.kafka.dto.FailedMessageListResponse
import com.hhplus.ecommerce.presentation.kafka.dto.FailedMessageResponse
import com.hhplus.ecommerce.presentation.kafka.dto.IgnoreMessageRequest
import com.hhplus.ecommerce.presentation.kafka.dto.ReprocessResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/failed-messages")
@Tag(name = "DLQ 관리", description = "DLQ 실패 메시지 조회 및 재처리 API")
class FailedMessageController(
    private val failedMessageService: FailedMessageService
) {

    @Operation(
        summary = "전체 실패 메시지 조회",
        description = "DLQ에 저장된 모든 실패 메시지를 최근순으로 조회합니다"
    )
    @GetMapping
    fun getAllFailedMessages(): ResponseEntity<FailedMessageListResponse> {
        val messages = failedMessageService.getAllFailedMessages()
        return ResponseEntity.ok(FailedMessageListResponse.from(messages))
    }

    @Operation(
        summary = "상태별 실패 메시지 조회",
        description = "특정 상태의 실패 메시지를 최근순으로 조회합니다. 상태: PENDING, REPROCESSED, IGNORED"
    )
    @GetMapping("/status/{status}")
    fun getFailedMessagesByStatus(
        @PathVariable status: FailedMessageStatus
    ): ResponseEntity<FailedMessageListResponse> {
        val messages = failedMessageService.getFailedMessagesByStatus(status)
        return ResponseEntity.ok(FailedMessageListResponse.from(messages))
    }

    @Operation(
        summary = "특정 실패 메시지 조회",
        description = "ID로 특정 실패 메시지의 상세 정보를 조회합니다"
    )
    @GetMapping("/{id}")
    fun getFailedMessageById(
        @PathVariable id: UUID
    ): ResponseEntity<FailedMessageResponse> {
        val message = failedMessageService.getFailedMessageById(id)
        return ResponseEntity.ok(FailedMessageResponse.from(message))
    }

    @Operation(
        summary = "실패 메시지 수동 재처리",
        description = "실패한 메시지를 Kafka로 재발행하여 수동으로 재처리합니다"
    )
    @PostMapping("/{id}/reprocess")
    fun reprocessFailedMessage(
        @PathVariable id: UUID
    ): ResponseEntity<ReprocessResponse> {
        val result = failedMessageService.reprocessFailedMessage(id)
        return ResponseEntity.ok(ReprocessResponse.from(result))
    }

    @Operation(
        summary = "실패 메시지 무시 처리",
        description = "재처리하지 않을 메시지를 무시 상태로 변경합니다"
    )
    @PostMapping("/{id}/ignore")
    fun ignoreFailedMessage(
        @PathVariable id: UUID,
        @RequestBody request: IgnoreMessageRequest
    ): ResponseEntity<Void> {
        failedMessageService.ignoreFailedMessage(id, request.note)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "대기 중인 메시지 개수 조회",
        description = "PENDING 상태인 실패 메시지의 개수를 조회합니다"
    )
    @GetMapping("/pending/count")
    fun getPendingCount(): ResponseEntity<Map<String, Long>> {
        val count = failedMessageService.getPendingCount()
        return ResponseEntity.ok(mapOf("count" to count))
    }
}