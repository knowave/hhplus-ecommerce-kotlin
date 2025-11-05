package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.payment.dto.*
import com.hhplus.ecommerce.presentation.payment.dto.OrderPaymentResponse
import com.hhplus.ecommerce.presentation.payment.dto.RetryTransmissionResponse
import com.hhplus.ecommerce.presentation.payment.dto.TransmissionDetailResponse

/**
 * 결제 비즈니스 로직 인터페이스
 */
interface PaymentService {

    /**
     * 결제 처리
     */
    fun processPayment(orderId: Long, request: ProcessPaymentCommand): ProcessPaymentResult

    /**
     * 결제 취소 (환불)
     */
    fun cancelPayment(paymentId: Long, request: CancelPaymentCommand): CancelPaymentResult

    /**
     * 결제 정보 조회
     */
    fun getPaymentDetail(paymentId: Long, userId: Long): PaymentDetailResult

    /**
     * 주문별 결제 내역 조회
     */
    fun getOrderPayment(orderId: Long, userId: Long): OrderPaymentResult

    /**
     * 데이터 전송 상태 조회
     */
    fun getTransmissionDetail(transmissionId: Long): TransmissionDetailResult

    /**
     * 데이터 전송 재시도
     */
    fun retryTransmission(transmissionId: Long): RetryTransmissionResult
}