package com.hhplus.ecommerce.application.order.dto

import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.OrderItemRequest

data class CreateOrderCommand(
    val userId: Long,
    val items: List<OrderItemCommand>,
    val couponId: Long? = null
) {
    companion object {
        fun command(request: CreateOrderRequest): CreateOrderCommand {
            return CreateOrderCommand(
                userId = request.userId,
                items = request.items.map { OrderItemCommand.command(it) }
            )
        }
    }
}

data class OrderItemCommand(
    val productId: Long,
    val quantity: Int
) {
    companion object {
        fun command(request: OrderItemRequest): OrderItemCommand {
            return OrderItemCommand(
                productId = request.productId,
                quantity = request.quantity
            )
        }
    }
}