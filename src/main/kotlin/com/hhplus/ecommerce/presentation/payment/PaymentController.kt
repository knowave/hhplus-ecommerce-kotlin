package com.hhplus.ecommerce.presentation.payment

import com.hhplus.ecommerce.application.payment.PaymentService
import com.hhplus.ecommerce.application.payment.dto.CancelPaymentCommand
import com.hhplus.ecommerce.application.payment.dto.ProcessPaymentCommand
import com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentRequest
import com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentResponse
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
        val command = ProcessPaymentCommand.command(request)
        val result = paymentService.processPayment(orderId, command)

        return ResponseEntity.ok(ProcessPaymentResponse.from(result))
    }

    @Operation(summary = "결제 취소", description = "결제 ID와 사용자 ID로 결제를 취소")
    @PostMapping("/cancel/{paymentId}")
    fun cancelPayment(
        @PathVariable paymentId: Long,
        @RequestBody request: CancelPaymentRequest
    ): ResponseEntity<CancelPaymentResponse> {
        val command = CancelPaymentCommand.from(request)
        val result = paymentService.cancelPayment(paymentId, command)

        return ResponseEntity.ok(CancelPaymentResponse.from(result))
    }

    @Operation(summary = "결제 정보 조회", description = "결제 ID와 사용자 ID로 결제 정보를 조회합니다")
    @GetMapping("/{paymentId}")
    fun getPaymentDetail(
        @PathVariable paymentId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<PaymentDetailResponse> {
        val result = paymentService.getPaymentDetail(paymentId, userId)
        return ResponseEntity.ok(PaymentDetailResponse.from(result))
    }

    @Operation(summary = "주문별 결제 내역 조회", description = "주문 ID와 사용자 ID로 결제 내역을 조회합니다")
    @GetMapping("/orders/{orderId}/payment")
    fun getOrderPayment(
        @PathVariable orderId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<OrderPaymentResponse> {
        val result = paymentService.getOrderPayment(orderId, userId)
        return ResponseEntity.ok(OrderPaymentResponse.from(result))
    }

    @Operation(summary = "데이터 전송 상태 조회", description = "전송 ID로 데이터 전송 상태를 조회합니다")
    @GetMapping("/data-transmissions/{transmissionId}")
    fun getTransmissionDetail(
        @PathVariable transmissionId: Long
    ): ResponseEntity<TransmissionDetailResponse> {
        val result = paymentService.getTransmissionDetail(transmissionId)
        return ResponseEntity.ok(TransmissionDetailResponse.from(result))
    }

    @Operation(summary = "데이터 전송 재시도", description = "전송 ID로 실패한 데이터 전송을 재시도합니다")
    @PostMapping("/data-transmissions/{transmissionId}/retry")
    fun retryTransmission(
        @PathVariable transmissionId: Long
    ): ResponseEntity<RetryTransmissionResponse> {
        val result = paymentService.retryTransmission(transmissionId)
        return ResponseEntity.ok(RetryTransmissionResponse.from(result))
    }
}
