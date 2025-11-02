package com.hhplus.ecommerce.domains.payment

import com.hhplus.ecommerce.domains.payment.dto.*

/**
 * 결제 비즈니스 로직 인터페이스
 */
interface PaymentService {

    /**
     * 결제 처리
     */
    fun processPayment(orderId: Long, request: ProcessPaymentRequest): ProcessPaymentResponse

    /**
     * 결제 정보 조회
     */
    fun getPaymentDetail(paymentId: Long, userId: Long): PaymentDetailResponse

    /**
     * 주문별 결제 내역 조회
     */
    fun getOrderPayment(orderId: Long, userId: Long): OrderPaymentResponse

    /**
     * 데이터 전송 상태 조회
     */
    fun getTransmissionDetail(transmissionId: Long): TransmissionDetailResponse

    /**
     * 데이터 전송 재시도
     */
    fun retryTransmission(transmissionId: Long): RetryTransmissionResponse
}
