package com.hhplus.ecommerce.domain.kafka.repository

import com.hhplus.ecommerce.domain.kafka.entity.FailedMessage
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

/**
 * 실패한 메시지 JPA Repository
 */
interface FailedMessageJpaRepository : JpaRepository<FailedMessage, UUID> {
    /**
     * 토픽별 대기중인 실패 메시지 조회
     */
    fun findByTopicAndStatus(topic: String, status: FailedMessageStatus): List<FailedMessage>

    /**
     * 상태별 실패 메시지 수 조회
     */
    fun countByStatus(status: FailedMessageStatus): Long
}