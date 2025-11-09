package com.hhplus.ecommerce.domain.payment

import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.Payment
import java.util.UUID

/**
 * 결제 데이터 접근 인터페이스
 */
interface PaymentRepository {
    fun findById(paymentId: UUID): Payment?

    fun findByOrderId(orderId: UUID): Payment?

    fun save(payment: Payment): Payment

    fun findTransmissionById(transmissionId: UUID): DataTransmission?

    fun findTransmissionByOrderId(orderId: UUID): DataTransmission?

    fun saveTransmission(transmission: DataTransmission): DataTransmission
}