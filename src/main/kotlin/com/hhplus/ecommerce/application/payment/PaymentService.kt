package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.payment.dto.*
import com.hhplus.ecommerce.presentation.payment.dto.OrderPaymentResponse
import com.hhplus.ecommerce.presentation.payment.dto.RetryTransmissionResponse
import com.hhplus.ecommerce.presentation.payment.dto.TransmissionDetailResponse
import java.util.UUID

/**
 * 결제 비즈니스 로직 인터페이스
 */
interface PaymentService {

    /**
     * 결제 처리
     */
    fun processPayment(orderId: UUID, request: ProcessPaymentCommand): ProcessPaymentResult

    /**
     * 결제 취소 (환불)
     */
    fun cancelPayment(paymentId: UUID, request: CancelPaymentCommand): CancelPaymentResult

    /**
     * 결제 정보 조회
     */
    fun getPaymentDetail(paymentId: UUID, userId: UUID): PaymentDetailResult

    /**
     * 주문별 결제 내역 조회
     */
    fun getOrderPayment(orderId: UUID, userId: UUID): OrderPaymentResult

    /**
     * 데이터 전송 상태 조회
     */
    fun getTransmissionDetail(transmissionId: UUID): TransmissionDetailResult

    /**
     * 데이터 전송 재시도
     */
    fun retryTransmission(transmissionId: UUID): RetryTransmissionResult
}