package com.hhplus.ecommerce.domains.order

import com.hhplus.ecommerce.domains.order.dto.*
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("orders")
class OrderController(
    private val orderService: OrderService
) {

    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<CreateOrderResponse> {
        val response = orderService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "주문 상세 조회", description = "주문 ID와 사용자 ID로 주문 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    fun getOrderDetail(
        @PathVariable orderId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<OrderDetailResponse> {
        val response = orderService.getOrderDetail(orderId, userId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "주문 목록 조회", description = "사용자 ID로 주문 목록을 조회합니다. 상태 필터링 및 페이징을 지원합니다")
    @GetMapping
    fun getOrders(
        @RequestParam userId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<OrderListResponse> {
        val response = orderService.getOrders(userId, status, page, size)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "주문 취소", description = "주문 ID로 주문을 취소합니다")
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @RequestBody request: CancelOrderRequest
    ): ResponseEntity<CancelOrderResponse> {
        val response = orderService.cancelOrder(orderId, request)
        return ResponseEntity.ok(response)
    }
}
