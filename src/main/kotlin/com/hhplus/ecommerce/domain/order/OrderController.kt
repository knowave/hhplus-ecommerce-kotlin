package com.hhplus.ecommerce.domain.order

import com.hhplus.ecommerce.domain.order.dto.CreateOrderRequestDto
import com.hhplus.ecommerce.domain.order.dto.CreateOrderResponseDto
import com.hhplus.ecommerce.domain.order.dto.OrderResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/orders")
class OrderController(
    private val orderService: OrderService
) {

    @PostMapping
    fun createOrder(
        @RequestBody request: CreateOrderRequestDto
    ): ResponseEntity<CreateOrderResponseDto> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrder(request))
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @PathVariable orderId: String
    ): ResponseEntity<OrderResponseDto> {
        return ResponseEntity.status(HttpStatus.OK)
            .body(orderService.getOrder(orderId))
    }

    @GetMapping("/user/{userId}")
    fun getUserOrders(
        @PathVariable userId: String
    ): ResponseEntity<List<OrderResponseDto>> {
        return ResponseEntity.status(HttpStatus.OK)
            .body(orderService.getUserOrders(userId))
    }
}