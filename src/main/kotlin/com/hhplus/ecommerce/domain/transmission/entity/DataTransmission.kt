package com.hhplus.ecommerce.domain.transmission.entity

import com.hhplus.ecommerce.common.entity.CustomBaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "data_transmissions",
    indexes = [
        Index(name = "idx_status_created", columnList = "status, created_at"),
        Index(name = "idx_order_id", columnList = "order_id")
    ]
)
class DataTransmission(
    id: String,

    @Column(name = "order_id", nullable = false)
    val orderId: String,

    @Column(name = "payload", columnDefinition = "JSON", nullable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TransmissionStatus = TransmissionStatus.PENDING,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
) : CustomBaseEntity(id) {

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }

    fun markAsSuccess() {
        status = TransmissionStatus.SUCCESS
        sentAt = LocalDateTime.now()
    }

    fun markAsFailed() {
        attempts++
        status = TransmissionStatus.FAILED
    }

    fun canRetry(): Boolean {
        return attempts < MAX_RETRY_ATTEMPTS && status != TransmissionStatus.SUCCESS
    }

    fun retry() {
        require(canRetry()) { "재시도 최대 횟수를 초과했거나 이미 성공한 전송입니다." }
        attempts++
        status = TransmissionStatus.PENDING
    }
}
