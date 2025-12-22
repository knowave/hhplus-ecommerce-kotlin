package com.hhplus.ecommerce.domain.kafka.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Kafka 메시지 처리 실패 시 DLQ로 전송된 메시지를 저장하는 엔티티
 *
 * 재시도 3회 실패 후 DLQ Consumer가 이 엔티티에 저장하여
 * 관리자가 수동으로 확인하고 재처리할 수 있도록 합니다.
 */
@Entity
@Table(
    name = "failed_messages",
    indexes = [
        Index(name = "idx_topic_status", columnList = "topic,status"),
        Index(name = "idx_failed_at", columnList = "failed_at")
    ]
)
class FailedMessage(
    /**
     * 원본 토픽 이름
     */
    @Column(nullable = false, length = 255)
    val topic: String,

    /**
     * 파티션 번호
     */
    @Column(name = "partition_number", nullable = false)
    val partition: Int,

    /**
     * Offset 위치
     */
    @Column(name = "message_offset", nullable = false)
    val offset: Long,

    /**
     * 메시지 키
     */
    @Column(length = 255)
    val messageKey: String?,

    /**
     * 메시지 페이로드 (JSON 형태)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    /**
     * 에러 메시지
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    val errorMessage: String,

    /**
     * 에러 스택 트레이스
     */
    @Column(columnDefinition = "TEXT")
    val stackTrace: String?,

    /**
     * 실패 시각
     */
    @Column(nullable = false)
    val failedAt: LocalDateTime,

    /**
     * 재시도 횟수 (Consumer 레벨에서 재시도한 횟수)
     */
    @Column(nullable = false)
    val retryCount: Int,

    /**
     * 처리 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FailedMessageStatus = FailedMessageStatus.PENDING,

    /**
     * 재처리 시각
     */
    @Column
    var reprocessedAt: LocalDateTime? = null,

    /**
     * 재처리 결과 메시지
     */
    @Column(columnDefinition = "TEXT")
    var reprocessNote: String? = null
) : BaseEntity() {

    /**
     * 재처리 완료 처리
     */
    fun markAsReprocessed(note: String? = null) {
        this.status = FailedMessageStatus.REPROCESSED
        this.reprocessedAt = LocalDateTime.now()
        this.reprocessNote = note
    }

    /**
     * 무시 처리
     */
    fun markAsIgnored(note: String) {
        this.status = FailedMessageStatus.IGNORED
        this.reprocessNote = note
    }
}

/**
 * 실패 메시지 처리 상태
 */
enum class FailedMessageStatus {
    PENDING,
    REPROCESSED,
    IGNORED
}