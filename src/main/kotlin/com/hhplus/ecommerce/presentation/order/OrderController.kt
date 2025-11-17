package com.hhplus.ecommerce.presentation.order

import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.order.dto.CancelOrderCommand
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderResponse
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderResponse
import com.hhplus.ecommerce.presentation.order.dto.OrderDetailResponse
import com.hhplus.ecommerce.presentation.order.dto.OrderListResponse
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID


@RestController
@RequestMapping("orders")
class OrderController(
    private val orderService: OrderService
) {

    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<CreateOrderResponse> {
        val command = CreateOrderCommand.command(request)
        val result = orderService.createOrder(command)

        return ResponseEntity.status(HttpStatus.CREATED).body(CreateOrderResponse.from(result))
    }

    @Operation(summary = "주문 상세 조회", description = "주문 ID와 사용자 ID로 주문 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    fun getOrderDetail(
        @PathVariable orderId: UUID,
        @RequestParam userId: UUID
    ): ResponseEntity<OrderDetailResponse> {
        val result = orderService.getOrderDetail(orderId, userId)
        return ResponseEntity.ok(OrderDetailResponse.from(result))
    }

    @Operation(summary = "주문 목록 조회", description = "사용자 ID로 주문 목록을 조회합니다. 상태 필터링 및 페이징을 지원합니다")
    @GetMapping
    fun getOrders(
        @RequestParam userId: UUID,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<OrderListResponse> {
        val result = orderService.getOrders(userId, status, page, size)
        return ResponseEntity.ok(OrderListResponse.from(result))
    }

    @Operation(summary = "주문 취소", description = "주문 ID로 주문을 취소합니다")
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: UUID,
        @RequestBody request: CancelOrderRequest
    ): ResponseEntity<CancelOrderResponse> {
        val command = CancelOrderCommand.command(request)
        val result = orderService.cancelOrder(orderId, command)
        return ResponseEntity.ok(CancelOrderResponse.from(result))
    }
}
