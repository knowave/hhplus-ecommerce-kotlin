package com.hhplus.ecommerce.presentation.kafka.dto

import com.hhplus.ecommerce.application.kafka.dto.FailedMessageDto
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import java.time.LocalDateTime
import java.util.*

data class FailedMessageResponse(
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
        fun from(dto: FailedMessageDto): FailedMessageResponse {
            return FailedMessageResponse(
                id = dto.id,
                topic = dto.topic,
                partition = dto.partition,
                offset = dto.offset,
                messageKey = dto.messageKey,
                payload = dto.payload,
                errorMessage = dto.errorMessage,
                stackTrace = dto.stackTrace,
                failedAt = dto.failedAt,
                retryCount = dto.retryCount,
                status = dto.status,
                reprocessedAt = dto.reprocessedAt,
                reprocessNote = dto.reprocessNote
            )
        }
    }
}

data class FailedMessageListResponse(
    val messages: List<FailedMessageResponse>,
    val totalCount: Int
) {
    companion object {
        fun from(dtos: List<FailedMessageDto>): FailedMessageListResponse {
            return FailedMessageListResponse(
                messages = dtos.map { FailedMessageResponse.from(it) },
                totalCount = dtos.size
            )
        }
    }
}