package com.hhplus.ecommerce.domains.payment

import com.hhplus.ecommerce.domains.payment.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class PaymentController(
    private val paymentService: PaymentService
) {

    /**
     * 1. 결제 처리
     * POST /api/orders/{orderId}/payment
     */
    @PostMapping("/api/orders/{orderId}/payment")
    fun processPayment(
        @PathVariable orderId: Long,
        @RequestBody request: ProcessPaymentRequest
    ): ResponseEntity<ProcessPaymentResponse> {
        val response = paymentService.processPayment(orderId, request)
        return ResponseEntity.ok(response)
    }

    /**
     * 2. 결제 정보 조회
     * GET /api/payments/{paymentId}?userId={userId}
     */
    @GetMapping("/api/payments/{paymentId}")
    fun getPaymentDetail(
        @PathVariable paymentId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<PaymentDetailResponse> {
        val response = paymentService.getPaymentDetail(paymentId, userId)
        return ResponseEntity.ok(response)
    }

    /**
     * 3. 주문별 결제 내역 조회
     * GET /api/orders/{orderId}/payment?userId={userId}
     */
    @GetMapping("/api/orders/{orderId}/payment")
    fun getOrderPayment(
        @PathVariable orderId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<OrderPaymentResponse> {
        val response = paymentService.getOrderPayment(orderId, userId)
        return ResponseEntity.ok(response)
    }

    /**
     * 4. 데이터 전송 상태 조회
     * GET /api/data-transmissions/{transmissionId}
     */
    @GetMapping("/api/data-transmissions/{transmissionId}")
    fun getTransmissionDetail(
        @PathVariable transmissionId: Long
    ): ResponseEntity<TransmissionDetailResponse> {
        val response = paymentService.getTransmissionDetail(transmissionId)
        return ResponseEntity.ok(response)
    }

    /**
     * 5. 데이터 전송 재시도
     * POST /api/data-transmissions/{transmissionId}/retry
     */
    @PostMapping("/api/data-transmissions/{transmissionId}/retry")
    fun retryTransmission(
        @PathVariable transmissionId: Long
    ): ResponseEntity<RetryTransmissionResponse> {
        val response = paymentService.retryTransmission(transmissionId)
        return ResponseEntity.ok(response)
    }
}
