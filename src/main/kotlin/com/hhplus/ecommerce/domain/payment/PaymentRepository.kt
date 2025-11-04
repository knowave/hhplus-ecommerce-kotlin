package com.hhplus.ecommerce.domain.payment

import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.Payment

/**
 * 결제 데이터 접근 인터페이스
 */
interface PaymentRepository {

    /**
     * 결제 ID로 조회
     */
    fun findById(paymentId: Long): Payment?

    /**
     * 주문 ID로 결제 조회
     */
    fun findByOrderId(orderId: Long): Payment?

    /**
     * 결제 저장
     */
    fun save(payment: Payment): Payment

    /**
     * 결제 ID 생성
     */
    fun generateId(): Long

    /**
     * 데이터 전송 ID로 조회
     */
    fun findTransmissionById(transmissionId: Long): DataTransmission?

    /**
     * 주문 ID로 데이터 전송 조회
     */
    fun findTransmissionByOrderId(orderId: Long): DataTransmission?

    /**
     * 데이터 전송 저장
     */
    fun saveTransmission(transmission: DataTransmission): DataTransmission

    /**
     * 데이터 전송 ID 생성
     */
    fun generateTransmissionId(): Long
}