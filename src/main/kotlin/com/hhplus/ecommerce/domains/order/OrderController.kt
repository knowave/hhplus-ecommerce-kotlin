package com.hhplus.ecommerce.domains.order

import com.hhplus.ecommerce.domains.order.dto.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<CreateOrderResponse> {
        val response = orderService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{orderId}")
    fun getOrderDetail(
        @PathVariable orderId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<OrderDetailResponse> {
        val response = orderService.getOrderDetail(orderId, userId)
        return ResponseEntity.ok(response)
    }

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

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @RequestBody request: CancelOrderRequest
    ): ResponseEntity<CancelOrderResponse> {
        val response = orderService.cancelOrder(orderId, request)
        return ResponseEntity.ok(response)
    }
}
