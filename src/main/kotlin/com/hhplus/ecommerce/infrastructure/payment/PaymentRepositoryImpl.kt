package com.hhplus.ecommerce.infrastructure.payment

import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.Payment
import com.hhplus.ecommerce.domain.payment.PaymentRepository
import org.springframework.stereotype.Repository

/**
 * 결제 인메모리 Repository 구현체
 *
 * 이유: 실제 DB 없이 메모리에서 결제 및 데이터 전송 데이터를 관리합니다.
 */
@Repository
class PaymentRepositoryImpl : PaymentRepository {

    // ID 자동 생성을 위한 카운터
    private var nextPaymentId: Long = 5001L
    private var nextTransmissionId: Long = 7001L

    // Mock 데이터 저장소
    private val payments: MutableMap<Long, Payment> = mutableMapOf()
    private val transmissions: MutableMap<Long, DataTransmission> = mutableMapOf()

    // orderId를 키로 하는 인덱스
    private val paymentsByOrderId: MutableMap<Long, Long> = mutableMapOf()
    private val transmissionsByOrderId: MutableMap<Long, Long> = mutableMapOf()

    override fun findById(paymentId: Long): Payment? {
        return payments[paymentId]
    }

    override fun findByOrderId(orderId: Long): Payment? {
        val paymentId = paymentsByOrderId[orderId] ?: return null
        return payments[paymentId]
    }

    override fun save(payment: Payment): Payment {
        payments[payment.paymentId] = payment
        paymentsByOrderId[payment.orderId] = payment.paymentId
        return payment
    }

    override fun generateId(): Long {
        return nextPaymentId++
    }

    override fun findTransmissionById(transmissionId: Long): DataTransmission? {
        return transmissions[transmissionId]
    }

    override fun findTransmissionByOrderId(orderId: Long): DataTransmission? {
        val transmissionId = transmissionsByOrderId[orderId] ?: return null
        return transmissions[transmissionId]
    }

    override fun saveTransmission(transmission: DataTransmission): DataTransmission {
        transmissions[transmission.transmissionId] = transmission
        transmissionsByOrderId[transmission.orderId] = transmission.transmissionId
        return transmission
    }

    override fun generateTransmissionId(): Long {
        return nextTransmissionId++
    }
}
