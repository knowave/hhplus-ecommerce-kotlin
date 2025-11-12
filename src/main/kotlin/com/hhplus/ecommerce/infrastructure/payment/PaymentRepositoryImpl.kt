package com.hhplus.ecommerce.infrastructure.payment

import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.Payment
import com.hhplus.ecommerce.domain.payment.repository.PaymentRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 결제 인메모리 Repository 구현체
 *
 * 이유: 실제 DB 없이 메모리에서 결제 및 데이터 전송 데이터를 관리합니다.
 */
@Repository
class PaymentRepositoryImpl : PaymentRepository {
    // Mock 데이터 저장소
    private val payments: MutableMap<UUID, Payment> = mutableMapOf()
    private val transmissions: MutableMap<UUID, DataTransmission> = mutableMapOf()

    // orderId를 키로 하는 인덱스
    private val paymentsByOrderId: MutableMap<UUID, UUID> = mutableMapOf()
    private val transmissionsByOrderId: MutableMap<UUID, UUID> = mutableMapOf()

    private fun assignId(payment: Payment) {
        if (payment.id == null) {
            val idField = payment.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(payment, UUID.randomUUID())
        }
    }

    private fun assignTransmissionId(transmission: DataTransmission) {
        if (transmission.id == null) {
            val idField = transmission.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(transmission, UUID.randomUUID())
        }
    }

    override fun findById(paymentId: UUID): Payment? {
        return payments[paymentId]
    }

    override fun findByOrderId(orderId: UUID): Payment? {
        val paymentId = paymentsByOrderId[orderId] ?: return null
        return payments[paymentId]
    }

    override fun save(payment: Payment): Payment {
        assignId(payment)
        payments[payment.id!!] = payment
        paymentsByOrderId[payment.orderId] = payment.id!!
        return payment
    }

    override fun findTransmissionById(transmissionId: UUID): DataTransmission? {
        return transmissions[transmissionId]
    }

    override fun findTransmissionByOrderId(orderId: UUID): DataTransmission? {
        val transmissionId = transmissionsByOrderId[orderId] ?: return null
        return transmissions[transmissionId]
    }

    override fun saveTransmission(transmission: DataTransmission): DataTransmission {
        assignTransmissionId(transmission)
        transmissions[transmission.id!!] = transmission
        transmissionsByOrderId[transmission.orderId] = transmission.id!!
        return transmission
    }
}
