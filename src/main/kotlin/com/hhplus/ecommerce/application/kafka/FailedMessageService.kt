package com.hhplus.ecommerce.application.kafka

import com.hhplus.ecommerce.application.kafka.dto.FailedMessageDto
import com.hhplus.ecommerce.application.kafka.dto.ReprocessResultDto
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import java.util.*

interface FailedMessageService {
    fun getAllFailedMessages(): List<FailedMessageDto>

    fun getFailedMessagesByStatus(status: FailedMessageStatus): List<FailedMessageDto>

    fun getFailedMessageById(id: UUID): FailedMessageDto

    fun reprocessFailedMessage(id: UUID): ReprocessResultDto

    /**
     * 실패 메시지 무시 처리
     *
     * @param id 무시할 메시지 ID
     * @param note 무시 사유
     */
    fun ignoreFailedMessage(id: UUID, note: String)

    /**
     * PENDING 상태 메시지 개수 조회
     */
    fun getPendingCount(): Long
}