package com.hhplus.ecommerce.domain.payment.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "data_transmission")
class DataTransmission(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val orderId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TransmissionStatus,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(nullable = false)
    val maxAttempts: Int = 3,

    @Column
    var sentAt: LocalDateTime? = null,

    @Column
    var nextRetryAt: LocalDateTime? = null,

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null
) : BaseEntity()

/**
 * 전송 상태
 */
enum class TransmissionStatus {
    PENDING,    // 전송 대기
    SUCCESS,    // 전송 성공
    FAILED      // 전송 실패 (최대 재시도 초과)
}
