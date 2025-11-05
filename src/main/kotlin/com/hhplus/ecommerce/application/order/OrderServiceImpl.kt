package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.order.dto.*
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.LockManager
import com.hhplus.ecommerce.domain.order.OrderRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.coupon.entity.*
import com.hhplus.ecommerce.presentation.order.dto.*
import com.hhplus.ecommerce.domain.order.entity.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Service
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val userService: UserService,
    private val cartService: CartService,
    private val lockManager: LockManager
) : OrderService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun createOrder(request: CreateOrderCommand): CreateOrderResult {
        // 1. 요청 검증
        validateOrderRequest(request)
        userService.getUser(request.userId)

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
            couponService.findCouponById(userCoupon.couponId)
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
                id = orderRepository.generateItemId(),
                productId = product.id,
                orderId = orderId,
                productName = product.name,
                quantity = item.quantity,
                unitPrice = product.price,
                subtotal = product.price * item.quantity
            )
        }

        val now = LocalDateTime.now()
        val order = Order(
            id = orderId,
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

        val productIds = products.values.map { it.id }
        cartService.deleteCarts(request.userId, productIds)

        // 8. 응답 생성
        return CreateOrderResult(
            orderId = order.id,
            userId = order.userId,
            orderNumber = order.orderNumber,
            items = orderItems.map { item ->
                OrderItemResult(
                    orderItemId = item.id,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal
                )
            },
            pricing = PricingInfoDto(
                totalAmount = totalAmount,
                discountAmount = discountAmount,
                finalAmount = finalAmount,
                appliedCoupon = coupon?.let {
                    AppliedCouponInfoDto(
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

    override fun getOrderDetail(orderId: Long, userId: Long): OrderDetailResult {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

        // 권한 확인
        if (order.userId != userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        val coupon = if (order.appliedCouponId != null) {
            couponService.findCouponById(order.appliedCouponId)
        } else {
            null
        }

        return OrderDetailResult(
            orderId = order.id,
            userId = order.userId,
            orderNumber = order.orderNumber,
            items = order.items.map { item ->
                OrderItemResult(
                    orderItemId = item.id,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal
                )
            },
            pricing = PricingInfoDto(
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                appliedCoupon = coupon?.let {
                    AppliedCouponInfoDto(
                        couponId = it.id,
                        couponName = it.name,
                        discountRate = it.discountRate
                    )
                }
            ),
            status = order.status.name,
            payment = if (order.status == OrderStatus.PAID) {
                PaymentInfoDto(
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

    override fun getOrders(userId: Long, status: String?, page: Int, size: Int): OrderListResult {
        // 사용자 존재 확인
        userService.getUser(userId)

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
            OrderSummaryDto(
                orderId = order.id,
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                status = order.status.name,
                itemCount = order.items.size,
                createdAt = order.createdAt.format(DATE_FORMATTER)
            )
        }

        val pagination = PaginationInfoDto(
            currentPage = page,
            totalPages = totalPages,
            totalElements = totalElements,
            size = size,
            hasNext = page < totalPages - 1,
            hasPrevious = page > 0
        )

        return OrderListResult(
            orders = orderSummaries,
            pagination = pagination
        )
    }

    override fun cancelOrder(orderId: Long, request: CancelOrderCommand): CancelOrderResult {
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

        // 권한 확인
        if (order.userId != request.userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        // 재고 복원
        val restoredStock = restoreStock(order.items)

        // 쿠폰 복원
        val restoredCoupon = if (order.appliedCouponId != null) {
            restoreCoupon(order.appliedCouponId, request.userId)
        } else {
            null
        }

        // 주문 상태 변경 (도메인 메서드 사용)
        order.cancel()
        orderRepository.save(order)

        return CancelOrderResult(
            orderId = order.id,
            status = order.status.name,
            cancelledAt = order.updatedAt.format(DATE_FORMATTER),
            refund = RefundInfoDto(
                restoredStock = restoredStock,
                restoredCoupon = restoredCoupon
            )
        )
    }

    override fun getOrder(id: Long): Order {
        return orderRepository.findById(id)
            ?: throw OrderNotFoundException(id)
    }

    override fun updateOrder(order: Order): Order {
        return orderRepository.save(order)
    }

    // --- Private Helper Methods ---

    /**
     * 주문 요청 검증
     */
    private fun validateOrderRequest(request: CreateOrderCommand) {
        if (request.items.isEmpty()) {
            throw InvalidOrderItemsException("empty order items")
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
    private fun validateAndGetProducts(items: List<OrderItemCommand>): Map<Long, Product> {
        val products = mutableMapOf<Long, Product>()

        items.forEach { item ->
            val product = productService.findProductById(item.productId)

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
     *
     * Product Lock을 사용하여 동시성을 제어합니다.
     * 데드락 방지를 위해 productId를 정렬하여 항상 동일한 순서로 Lock을 획득합니다.
     * Product 도메인 엔티티의 deductStock() 메서드를 사용하여 재고를 차감합니다.
     */
    private fun deductStock(items: List<OrderItemCommand>, products: Map<Long, Product>) {
        val productIds = items.map { it.productId }

        lockManager.executeWithProductLocks(productIds) {
            items.forEach { item ->
                val product = products[item.productId]!!
                product.deductStock(item.quantity)
                productService.updateProduct(product)
            }
        }
    }

    /**
     * 쿠폰 검증 및 사용 처리
     * UserCoupon 도메인 엔티티의 use() 메서드를 사용하여 쿠폰을 사용합니다.
     */
    private fun validateAndUseCoupon(couponId: Long, userId: Long): UserCoupon {
        // 사용자의 쿠폰 조회
        val userCoupon = couponService.findUserCoupon(userId, couponId)

        // 쿠폰 사용 처리 (검증 로직 포함)
        userCoupon.use()  // 도메인 메서드 사용
        couponService.updateUserCoupon(userCoupon)

        return userCoupon
    }

    /**
     * 총 금액 계산
     */
    private fun calculateTotalAmount(items: List<OrderItemCommand>, products: Map<Long, Product>): Long {
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
     *
     * Product Lock을 사용하여 동시성을 제어합니다.
     * Product 도메인 엔티티의 restoreStock() 메서드를 사용하여 재고를 복원합니다.
     */
    private fun restoreStock(items: List<OrderItem>): List<RestoredStockItemDto> {
        val productIds = items.map { it.productId }

        return lockManager.executeWithProductLocks(productIds) {
            items.map { item ->
                val product = productService.findProductById(item.productId)

                product.restoreStock(item.quantity)  // 도메인 메서드 사용
                productService.updateProduct(product)

                RestoredStockItemDto(
                    productId = item.productId,
                    quantity = item.quantity
                )
            }
        }
    }

    /**
     * 쿠폰 복원 (만료되지 않은 경우만)
     * UserCoupon 도메인 엔티티의 restore() 메서드를 사용하여 쿠폰을 복원합니다.
     */
    private fun restoreCoupon(couponId: Long, userId: Long): RestoredCouponInfoDto {
        val userCoupon = couponService.findUserCoupon(userId, couponId)

        return lockManager.executeWithCouponLock(couponId) {
            // 쿠폰 복원 처리 (만료 체크 포함)
            userCoupon.restore()
            couponService.updateUserCoupon(userCoupon)

            RestoredCouponInfoDto(
                couponId = couponId,
                status = userCoupon.status.name
            )
        }
    }
}