package com.hhplus.ecommerce.application.kafka.dto

import com.hhplus.ecommerce.domain.kafka.entity.FailedMessage
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import java.time.LocalDateTime
import java.util.*

/**
 * 실패 메시지 조회 DTO
 */
data class FailedMessageDto(
    val id: UUID,
    val topic: String,
    val partition: Int,
    val offset: Long,
    val messageKey: String?,
    val payload: String,
    val errorMessage: String,
    val stackTrace: String?,
    val failedAt: LocalDateTime,
    val retryCount: Int,
    val status: FailedMessageStatus,
    val reprocessedAt: LocalDateTime?,
    val reprocessNote: String?
) {
    companion object {
        fun from(entity: FailedMessage): FailedMessageDto {
            return FailedMessageDto(
                id = entity.id!!,
                topic = entity.topic,
                partition = entity.partition,
                offset = entity.offset,
                messageKey = entity.messageKey,
                payload = entity.payload,
                errorMessage = entity.errorMessage,
                stackTrace = entity.stackTrace,
                failedAt = entity.failedAt,
                retryCount = entity.retryCount,
                status = entity.status,
                reprocessedAt = entity.reprocessedAt,
                reprocessNote = entity.reprocessNote
            )
        }
    }
}