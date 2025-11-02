package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.common.exception.CannotCancelOrderException
import com.hhplus.ecommerce.common.exception.CouponNotFoundException
import com.hhplus.ecommerce.common.exception.ExpiredCouponException
import com.hhplus.ecommerce.common.exception.ForbiddenException
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.common.exception.InvalidCouponException
import com.hhplus.ecommerce.common.exception.InvalidOrderItemsException
import com.hhplus.ecommerce.common.exception.InvalidQuantityException
import com.hhplus.ecommerce.common.exception.OrderNotFoundException
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.infrastructure.coupon.CouponRepository
import com.hhplus.ecommerce.infrastructure.coupon.CouponStatus
import com.hhplus.ecommerce.infrastructure.order.OrderRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepository
import com.hhplus.ecommerce.model.product.Product
import com.hhplus.ecommerce.infrastructure.user.UserRepository
import com.hhplus.ecommerce.model.coupon.Coupon
import com.hhplus.ecommerce.model.coupon.UserCoupon
import com.hhplus.ecommerce.presentation.order.dto.AppliedCouponInfo
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CancelOrderResponse
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderRequest
import com.hhplus.ecommerce.presentation.order.dto.CreateOrderResponse
import com.hhplus.ecommerce.model.order.Order
import com.hhplus.ecommerce.presentation.order.dto.OrderDetailResponse
import com.hhplus.ecommerce.model.order.OrderItem
import com.hhplus.ecommerce.presentation.order.dto.OrderItemRequest
import com.hhplus.ecommerce.presentation.order.dto.OrderItemResponse
import com.hhplus.ecommerce.presentation.order.dto.OrderListResponse
import com.hhplus.ecommerce.model.order.OrderStatus
import com.hhplus.ecommerce.presentation.order.dto.OrderSummary
import com.hhplus.ecommerce.presentation.order.dto.PaginationInfo
import com.hhplus.ecommerce.presentation.order.dto.PaymentInfo
import com.hhplus.ecommerce.presentation.order.dto.PricingInfo
import com.hhplus.ecommerce.presentation.order.dto.RefundInfo
import com.hhplus.ecommerce.presentation.order.dto.RestoredCouponInfo
import com.hhplus.ecommerce.presentation.order.dto.RestoredStockItem
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * 주문 서비스 구현체
 *
 * 비즈니스 정책에 따른 주문 처리:
 * 1. 주문 생성 시: 상품 검증 → 재고 차감 → 쿠폰 사용 → 주문 생성(PENDING)
 * 2. 주문 취소 시: 재고 복원 → 쿠폰 복원 → 주문 상태 변경(CANCELLED)
 */
@Service
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository
) : OrderService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun createOrder(request: CreateOrderRequest): CreateOrderResponse {
        // 1. 요청 검증
        validateOrderRequest(request)

        userRepository.findById(request.userId)
            ?: throw UserNotFoundException(request.userId)

        // 3. 상품 검증 및 재고 확인
        val products = validateAndGetProducts(request.items)

        // 4. 재고 차감 (비즈니스 정책: 주문 생성 시점에 즉시 차감)
        deductStock(request.items, products)

        // 5. 쿠폰 검증 및 사용 처리
        val userCoupon = if (request.couponId != null) {
            val validatedCoupon = validateAndUseCoupon(request.couponId, request.userId)
            validatedCoupon
        } else {
            null
        }

        val coupon = if (userCoupon != null) {
            couponRepository.findById(userCoupon.couponId)
        } else {
            null
        }

        // 6. 금액 계산
        val totalAmount = calculateTotalAmount(request.items, products)
        val discountAmount = calculateDiscountAmount(totalAmount, coupon)
        val finalAmount = totalAmount - discountAmount

        // 7. 주문 생성
        val orderId = orderRepository.generateId()
        val orderNumber = orderRepository.generateOrderNumber(orderId)

        val orderItems = request.items.map { item ->
            val product = products[item.productId]!!
            OrderItem(
                orderItemId = orderRepository.generateItemId(),
                productId = product.id,
                productName = product.name,
                quantity = item.quantity,
                unitPrice = product.price,
                subtotal = product.price * item.quantity
            )
        }

        val now = LocalDateTime.now()
        val order = Order(
            orderId = orderId,
            userId = request.userId,
            orderNumber = orderNumber,
            items = orderItems,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            finalAmount = finalAmount,
            appliedCouponId = request.couponId,
            status = OrderStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        orderRepository.save(order)

        // 8. 응답 생성
        return CreateOrderResponse(
            orderId = order.orderId,
            userId = order.userId,
            orderNumber = order.orderNumber,
            items = orderItems.map { item ->
                OrderItemResponse(
                    orderItemId = item.orderItemId,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal
                )
            },
            pricing = PricingInfo(
                totalAmount = totalAmount,
                discountAmount = discountAmount,
                finalAmount = finalAmount,
                appliedCoupon = coupon?.let {
                    AppliedCouponInfo(
                        couponId = it.id,
                        couponName = it.name,
                        discountRate = it.discountRate
                    )
                }
            ),
            status = order.status.name,
            createdAt = order.createdAt.format(DATE_FORMATTER)
        )
    }

    override fun getOrderDetail(orderId: Long, userId: Long): OrderDetailResponse {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

        // 권한 확인
        if (order.userId != userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        val coupon = if (order.appliedCouponId != null) {
            couponRepository.findById(order.appliedCouponId)
        } else {
            null
        }

        return OrderDetailResponse(
            orderId = order.orderId,
            userId = order.userId,
            orderNumber = order.orderNumber,
            items = order.items.map { item ->
                OrderItemResponse(
                    orderItemId = item.orderItemId,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal
                )
            },
            pricing = PricingInfo(
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                appliedCoupon = coupon?.let {
                    AppliedCouponInfo(
                        couponId = it.id,
                        couponName = it.name,
                        discountRate = it.discountRate
                    )
                }
            ),
            status = order.status.name,
            payment = if (order.status == OrderStatus.PAID) {
                PaymentInfo(
                    paidAmount = order.finalAmount,
                    paidAt = order.updatedAt.format(DATE_FORMATTER)
                )
            } else {
                null
            },
            createdAt = order.createdAt.format(DATE_FORMATTER),
            updatedAt = order.updatedAt.format(DATE_FORMATTER)
        )
    }

    override fun getOrders(userId: Long, status: String?, page: Int, size: Int): OrderListResponse {
        // 사용자 존재 확인
        userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        val orders = if (status != null) {
            val orderStatus = try {
                OrderStatus.valueOf(status.uppercase())
            } catch (e: IllegalArgumentException) {
                throw InvalidOrderItemsException("Invalid order status: $status")
            }
            orderRepository.findByUserIdAndStatus(userId, orderStatus)
        } else {
            orderRepository.findByUserId(userId)
        }

        // 페이지네이션
        val totalElements = orders.size
        val totalPages = ceil(totalElements.toDouble() / size).toInt()
        val start = page * size
        val end = minOf(start + size, totalElements)

        val pagedOrders = if (start < totalElements) {
            orders.subList(start, end)
        } else {
            emptyList()
        }

        val orderSummaries = pagedOrders.map { order ->
            OrderSummary(
                orderId = order.orderId,
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                status = order.status.name,
                itemCount = order.items.size,
                createdAt = order.createdAt.format(DATE_FORMATTER)
            )
        }

        val pagination = PaginationInfo(
            currentPage = page,
            totalPages = totalPages,
            totalElements = totalElements,
            size = size,
            hasNext = page < totalPages - 1,
            hasPrevious = page > 0
        )

        return OrderListResponse(
            orders = orderSummaries,
            pagination = pagination
        )
    }

    override fun cancelOrder(orderId: Long, request: CancelOrderRequest): CancelOrderResponse {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

        // 권한 확인
        if (order.userId != request.userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        // 취소 가능 상태 확인 (PENDING만 취소 가능)
        if (order.status != OrderStatus.PENDING) {
            throw CannotCancelOrderException(orderId, order.status.name)
        }

        // 재고 복원
        val restoredStock = restoreStock(order.items)

        // 쿠폰 복원
        val restoredCoupon = if (order.appliedCouponId != null) {
            restoreCoupon(order.appliedCouponId, request.userId)
        } else {
            null
        }

        // 주문 상태 변경
        order.status = OrderStatus.CANCELLED
        order.updatedAt = LocalDateTime.now()
        orderRepository.save(order)

        return CancelOrderResponse(
            orderId = order.orderId,
            status = order.status.name,
            cancelledAt = order.updatedAt.format(DATE_FORMATTER),
            refund = RefundInfo(
                restoredStock = restoredStock,
                restoredCoupon = restoredCoupon
            )
        )
    }

    // --- Private Helper Methods ---

    /**
     * 주문 요청 검증
     */
    private fun validateOrderRequest(request: CreateOrderRequest) {
        if (request.items.isEmpty()) {
            throw InvalidOrderItemsException("주문 상품 목록이 비어있습니다.")
        }

        request.items.forEach { item ->
            if (item.quantity < 1) {
                throw InvalidQuantityException(item.quantity)
            }
        }
    }

    /**
     * 상품 검증 및 조회
     */
    private fun validateAndGetProducts(items: List<OrderItemRequest>): Map<Long, Product> {
        val products = mutableMapOf<Long, Product>()

        items.forEach { item ->
            val product = productRepository.findById(item.productId)
                ?: throw ProductNotFoundException(item.productId)

            // 재고 확인
            if (product.stock < item.quantity) {
                throw InsufficientStockException(
                    productId = product.id,
                    requested = item.quantity,
                    available = product.stock
                )
            }

            products[product.id] = product
        }

        return products
    }

    /**
     * 재고 차감 (비즈니스 정책: 주문 생성 시점에 즉시 차감)
     */
    private fun deductStock(items: List<OrderItemRequest>, products: Map<Long, Product>) {
        items.forEach { item ->
            val product = products[item.productId]!!
            product.stock -= item.quantity
            productRepository.save(product)
        }
    }

    /**
     * 쿠폰 검증 및 사용 처리
     */
    private fun validateAndUseCoupon(couponId: Long, userId: Long): UserCoupon {
        // 사용자의 쿠폰 조회
        val userCoupon = couponRepository.findUserCoupon(userId, couponId)
            ?: throw CouponNotFoundException(couponId)

        // 쿠폰 상태 확인
        if (userCoupon.status != CouponStatus.AVAILABLE) {
            throw InvalidCouponException("쿠폰을 사용할 수 없습니다. 상태: ${userCoupon.status}")
        }

        // 쿠폰 만료 확인
        val expiresAt = LocalDateTime.parse(userCoupon.expiresAt, DATE_FORMATTER)
        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw ExpiredCouponException(couponId)
        }

        // 쿠폰 사용 처리
        userCoupon.status = CouponStatus.USED
        userCoupon.usedAt = LocalDateTime.now().format(DATE_FORMATTER)
        couponRepository.saveUserCoupon(userCoupon)

        return userCoupon
    }

    /**
     * 총 금액 계산
     */
    private fun calculateTotalAmount(items: List<OrderItemRequest>, products: Map<Long, Product>): Long {
        return items.sumOf { item ->
            val product = products[item.productId]!!
            product.price * item.quantity
        }
    }

    /**
     * 할인 금액 계산 (비즈니스 정책: 소수점 이하 내림)
     */
    private fun calculateDiscountAmount(totalAmount: Long, coupon: Coupon?): Long {
        if (coupon == null) return 0L
        return totalAmount * coupon.discountRate / 100
    }

    /**
     * 재고 복원
     */
    private fun restoreStock(items: List<OrderItem>): List<RestoredStockItem> {
        return items.map { item ->
            val product = productRepository.findById(item.productId)
                ?: throw ProductNotFoundException(item.productId)

            product.stock += item.quantity
            productRepository.save(product)

            RestoredStockItem(
                productId = item.productId,
                quantity = item.quantity
            )
        }
    }

    /**
     * 쿠폰 복원 (만료되지 않은 경우만)
     */
    private fun restoreCoupon(couponId: Long, userId: Long): RestoredCouponInfo? {
        val userCoupon = couponRepository.findUserCoupon(userId, couponId)
            ?: return null

        // 만료된 쿠폰은 복원하지 않음
        val expiresAt = LocalDateTime.parse(userCoupon.expiresAt, DATE_FORMATTER)
        if (expiresAt.isBefore(LocalDateTime.now())) {
            return RestoredCouponInfo(
                couponId = couponId,
                status = CouponStatus.EXPIRED.name
            )
        }

        // USED → AVAILABLE로 복원
        userCoupon.status = CouponStatus.AVAILABLE
        userCoupon.usedAt = null
        couponRepository.saveUserCoupon(userCoupon)

        return RestoredCouponInfo(
            couponId = couponId,
            status = CouponStatus.AVAILABLE.name
        )
    }
}