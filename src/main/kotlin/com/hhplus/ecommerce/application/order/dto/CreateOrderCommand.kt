package com.hhplus.ecommerce.application.order.dto

import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.OrderItemRequest
import java.util.UUID

data class CreateOrderCommand(
    val userId: UUID,
    val items: List<OrderItemCommand>,
    val couponId: UUID? = null
) {
    companion object {
        fun command(request: CreateOrderRequest): CreateOrderCommand {
            return CreateOrderCommand(
                userId = request.userId,
                items = request.items.map { OrderItemCommand.command(it) },
                couponId = request.couponId
            )
        }
    }
}

data class OrderItemCommand(
    val productId: UUID,
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