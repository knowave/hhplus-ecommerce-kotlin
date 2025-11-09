package com.hhplus.ecommerce.domain.shipping.entity

import com.hhplus.ecommerce.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "shipping")
class Shipping(
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    val orderId: UUID,

    @Column(nullable = false, length = 100)
    val carrier: String,

    @Column(nullable = false, length = 100)
    val trackingNumber: String,

    @Column
    val shippingStartAt: LocalDateTime?,

    @Column(nullable = false)
    val estimatedArrivalAt: LocalDateTime,

    @Column
    val deliveredAt: LocalDateTime?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ShippingStatus,

    @Column(nullable = false)
    val isDelayed: Boolean = false,

    @Column(nullable = false)
    val isExpired: Boolean = false
) : BaseEntity()

/**
 * 배송 상태
 */
enum class ShippingStatus {
    PENDING,      // 배송 대기
    IN_TRANSIT,   // 배송 중
    DELIVERED     // 배송 완료
}
