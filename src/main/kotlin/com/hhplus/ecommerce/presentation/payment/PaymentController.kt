package com.hhplus.ecommerce.presentation.payment

import com.hhplus.ecommerce.application.payment.PaymentService
import com.hhplus.ecommerce.presentation.payment.dto.OrderPaymentResponse
import com.hhplus.ecommerce.presentation.payment.dto.PaymentDetailResponse
import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentRequest
import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentResponse
import com.hhplus.ecommerce.presentation.payment.dto.RetryTransmissionResponse
import com.hhplus.ecommerce.presentation.payment.dto.TransmissionDetailResponse
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @Operation(summary = "결제 처리", description = "주문 ID로 결제를 처리합니다")
    @PostMapping("/orders/{orderId}/payment")
    fun processPayment(
        @PathVariable orderId: Long,
        @RequestBody request: ProcessPaymentRequest
    ): ResponseEntity<ProcessPaymentResponse> {
        val response = paymentService.processPayment(orderId, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "결제 정보 조회", description = "결제 ID와 사용자 ID로 결제 정보를 조회합니다")
    @GetMapping("/{paymentId}")
    fun getPaymentDetail(
        @PathVariable paymentId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<PaymentDetailResponse> {
        val response = paymentService.getPaymentDetail(paymentId, userId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "주문별 결제 내역 조회", description = "주문 ID와 사용자 ID로 결제 내역을 조회합니다")
    @GetMapping("/orders/{orderId}/payment")
    fun getOrderPayment(
        @PathVariable orderId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<OrderPaymentResponse> {
        val response = paymentService.getOrderPayment(orderId, userId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "데이터 전송 상태 조회", description = "전송 ID로 데이터 전송 상태를 조회합니다")
    @GetMapping("/data-transmissions/{transmissionId}")
    fun getTransmissionDetail(
        @PathVariable transmissionId: Long
    ): ResponseEntity<TransmissionDetailResponse> {
        val response = paymentService.getTransmissionDetail(transmissionId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "데이터 전송 재시도", description = "전송 ID로 실패한 데이터 전송을 재시도합니다")
    @PostMapping("/data-transmissions/{transmissionId}/retry")
    fun retryTransmission(
        @PathVariable transmissionId: Long
    ): ResponseEntity<RetryTransmissionResponse> {
        val response = paymentService.retryTransmission(transmissionId)
        return ResponseEntity.ok(response)
    }
}
