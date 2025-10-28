package com.hhplus.ecommerce.domain.order

import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository
import com.hhplus.ecommerce.domain.coupon.entity.CouponStatus
import com.hhplus.ecommerce.domain.order.dto.*
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderItem
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val userCouponRepository: UserCouponRepository
) {

    @Transactional
    fun createOrder(request: CreateOrderRequestDto): CreateOrderResponseDto {
        // 1. 사용자 조회
        val user = userRepository.findById(request.userId)
            .orElseThrow { UserNotFoundException(request.userId) }

        // 2. 상품 검증 및 재고 차감
        data class OrderItemData(
            val product: com.hhplus.ecommerce.domain.product.entity.Product,
            val quantity: Int,
            val unitPrice: BigDecimal,
            val subtotal: BigDecimal
        )

        val orderItemsData = mutableListOf<OrderItemData>()
        var totalAmount = BigDecimal.ZERO

        request.items.forEach { itemRequest ->
            // 상품 조회
            val product = productRepository.findById(itemRequest.productId)
                .orElseThrow { ProductNotFoundException(itemRequest.productId) }

            // 수량 검증
            if (itemRequest.quantity <= 0) {
                throw InvalidQuantityException(itemRequest.quantity)
            }

            // 재고 검증 및 차감
            if (product.stock < itemRequest.quantity) {
                throw InsufficientStockException(
                    product.id,
                    itemRequest.quantity,
                    product.stock
                )
            }

            // 재고 차감
            product.decreaseStock(itemRequest.quantity)
            productRepository.save(product)

            // 금액 계산
            val unitPrice = product.price
            val subtotal = unitPrice.multiply(BigDecimal(itemRequest.quantity))
            totalAmount = totalAmount.add(subtotal)

            orderItemsData.add(
                OrderItemData(
                    product = product,
                    quantity = itemRequest.quantity,
                    unitPrice = unitPrice,
                    subtotal = subtotal
                )
            )
        }

        var discountAmount = BigDecimal.ZERO
        if (request.couponId != null) {
            val userCoupon = userCouponRepository.findByUserIdAndCouponIdAndStatus(
                request.userId,
                request.couponId,
                CouponStatus.AVAILABLE
            ) ?: throw InvalidCouponException("No available coupon found for user")

            userCoupon.use()
            userCouponRepository.save(userCoupon)

            val discountRate = BigDecimal(userCoupon.coupon.discountRate).divide(BigDecimal(100))
            discountAmount = totalAmount.multiply(discountRate).setScale(0, RoundingMode.DOWN)
        }

        val finalAmount = totalAmount.subtract(discountAmount)

        if (user.balance < finalAmount) {
            throw InsufficientBalanceException(finalAmount, user.balance)
        }

        val order = Order(
            id = UUID.randomUUID().toString(),
            user = user,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            finalAmount = finalAmount,
            status = OrderStatus.PENDING
        )

        orderItemsData.forEach { itemData ->
            val orderItem = OrderItem(
                id = UUID.randomUUID().toString(),
                order = order,
                product = itemData.product,
                quantity = itemData.quantity,
                unitPrice = itemData.unitPrice,
                subtotal = itemData.subtotal
            )
            order.addItem(orderItem)
        }

        val savedOrder = orderRepository.save(order)

        return CreateOrderResponseDto(
            orderId = savedOrder.id,
            items = savedOrder.items.map { item ->
                OrderItemResponseDto(
                    productId = item.product.id,
                    name = item.product.name,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal
                )
            },
            totalAmount = savedOrder.totalAmount,
            discountAmount = savedOrder.discountAmount,
            finalAmount = savedOrder.finalAmount,
            status = savedOrder.status
        )
    }

    @Transactional(readOnly = true)
    fun getOrder(orderId: String): OrderResponseDto {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException(orderId) }

        return OrderResponseDto(
            orderId = order.id,
            userId = order.user.id,
            items = order.items.map { item ->
                OrderItemResponseDto(
                    productId = item.product.id,
                    name = item.product.name,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal
                )
            },
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            finalAmount = order.finalAmount,
            status = order.status,
            createdAt = order.createdAt,
            paidAt = order.paidAt
        )
    }

    @Transactional(readOnly = true)
    fun getUserOrders(userId: String): List<OrderResponseDto> {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException(userId)
        }

        val orders = orderRepository.findByUserId(userId)

        return orders.map { order ->
            OrderResponseDto(
                orderId = order.id,
                userId = order.user.id,
                items = order.items.map { item ->
                    OrderItemResponseDto(
                        productId = item.product.id,
                        name = item.product.name,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        subtotal = item.subtotal
                    )
                },
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                status = order.status,
                createdAt = order.createdAt,
                paidAt = order.paidAt
            )
        }
    }
}