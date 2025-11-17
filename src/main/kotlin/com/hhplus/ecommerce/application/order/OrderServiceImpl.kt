package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.order.dto.*
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.coupon.entity.*
import com.hhplus.ecommerce.domain.order.entity.*
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class OrderServiceImpl(
    private val orderRepository: OrderJpaRepository,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val userService: UserService,
    private val cartService: CartService
) : OrderService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    @Transactional
    override fun createOrder(request: CreateOrderCommand): CreateOrderResult {
        // 1. 요청 검증
        validateOrderRequest(request)
        val user = userService.getUser(request.userId)

        // 3. 재고 차감 (비즈니스 정책: 주문 생성 시점에 즉시 차감)
        // 비관적 락으로 상품 조회 및 재고 차감 (검증 포함)
        val products = deductStock(request.items)

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
        val orderNumber = generateOrderNumber()
        val order = Order(
            userId = request.userId,
            orderNumber = orderNumber,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            finalAmount = finalAmount,
            appliedCouponId = request.couponId,
            status = OrderStatus.PENDING
        )

        val orderItems = request.items.map { item ->
            val product = products[item.productId]!!
            OrderItem(
                productId = product.id!!,
                userId = user.id!!,
                order = order,
                productName = product.name,
                quantity = item.quantity,
                unitPrice = product.price,
                subtotal = product.price * item.quantity
            )
        }

        // OrderItem을 Order의 items에 추가하고 저장 (Cascade로 OrderItem도 함께 저장됨)
        order.items.addAll(orderItems)
        val savedOrder = orderRepository.save(order)

        val productIds = products.values.map { it.id!! }
        cartService.deleteCarts(request.userId, productIds)

        // 8. 응답 생성
        return CreateOrderResult(
            orderId = savedOrder.id!!,
            userId = savedOrder.userId,
            orderNumber = savedOrder.orderNumber,
            items = orderItems.map { item ->
                OrderItemResult(
                    orderItemId = item.id!!,
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
                        couponId = it.id!!,
                        couponName = it.name,
                        discountRate = it.discountRate
                    )
                }
            ),
            status = order.status.name,
            createdAt = order.createdAt!!.toString()
        )
    }

    override fun getOrderDetail(orderId: UUID, userId: UUID): OrderDetailResult {
        val order = orderRepository.findById(orderId)
            .orElseThrow{ throw OrderNotFoundException(orderId) }

        // 권한 확인
        if (order.userId != userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        val coupon = if (order.appliedCouponId != null) {
            couponService.findCouponById(order.appliedCouponId!!)
        } else {
            null
        }

        return OrderDetailResult(
            orderId = order.id!!,
            userId = order.userId,
            orderNumber = order.orderNumber,
            items = order.items.map { item ->
                OrderItemResult(
                    orderItemId = item.id!!,
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
                        couponId = it.id!!,
                        couponName = it.name,
                        discountRate = it.discountRate
                    )
                }
            ),
            status = order.status.name,
            payment = if (order.status == OrderStatus.PAID) {
                PaymentInfoDto(
                    paidAmount = order.finalAmount,
                    paidAt = order.updatedAt!!.format(DATE_FORMATTER)
                )
            } else {
                null
            },
            createdAt = order.createdAt!!.format(DATE_FORMATTER),
            updatedAt = order.updatedAt!!.format(DATE_FORMATTER)
        )
    }

    override fun getOrders(userId: UUID, status: String?, page: Int, size: Int): OrderListResult {
        // 사용자 존재 확인
        userService.getUser(userId)

        // 상태 파라미터 변환 (nullable)
        val orderStatus = status?.let {
            try {
                OrderStatus.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                throw InvalidOrderItemsException("Invalid order status: $status")
            }
        }

        // Pageable 생성 (최신순 정렬은 Repository의 @Query에서 처리)
        val pageable = PageRequest.of(page, size)

        // DB에서 페이징 처리된 데이터 조회 (LIMIT, OFFSET 자동 적용)
        val orderPage = orderRepository.findByUserIdWithPaging(userId, orderStatus, pageable)

        // DTO 변환
        val orderSummaries = orderPage.content.map { order ->
            OrderSummaryDto(
                orderId = order.id!!,
                orderNumber = order.orderNumber,
                totalAmount = order.totalAmount,
                discountAmount = order.discountAmount,
                finalAmount = order.finalAmount,
                status = order.status.name,
                itemCount = order.items.size,
                createdAt = order.createdAt!!.format(DATE_FORMATTER)
            )
        }

        // Page 객체에서 페이징 정보 추출
        val pagination = PaginationInfoDto(
            currentPage = orderPage.number,
            totalPages = orderPage.totalPages,
            totalElements = orderPage.totalElements.toInt(),
            size = orderPage.size,
            hasNext = orderPage.hasNext(),
            hasPrevious = orderPage.hasPrevious()
        )

        return OrderListResult(
            orders = orderSummaries,
            pagination = pagination
        )
    }

    @Transactional
    override fun cancelOrder(orderId: UUID, request: CancelOrderCommand): CancelOrderResult {
        val order = orderRepository.findById(orderId)
            .orElseThrow { OrderNotFoundException(orderId) }

        // 권한 확인
        if (order.userId != request.userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        // 재고 복원
        val restoredStock = restoreStock(order.items)

        // 쿠폰 복원
        val restoredCoupon = if (order.appliedCouponId != null) {
            restoreCoupon(order.appliedCouponId!!, request.userId)
        } else {
            null
        }

        // 주문 상태 변경 (도메인 메서드 사용)
        order.cancel()
        orderRepository.save(order)

        return CancelOrderResult(
            orderId = order.id!!,
            status = order.status.name,
            cancelledAt = order.updatedAt!!.toString(),
            refund = RefundInfoDto(
                restoredStock = restoredStock,
                restoredCoupon = restoredCoupon
            )
        )
    }

    override fun getOrder(id: UUID): Order {
        return orderRepository.findById(id)
            .orElseThrow { OrderNotFoundException(id) }
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
    private fun validateAndGetProducts(items: List<OrderItemCommand>): Map<UUID, Product> {
        val products = mutableMapOf<UUID, Product>()

        items.forEach { item ->
            val product = productService.findProductById(item.productId)

            // 재고 확인
            if (product.stock < item.quantity) {
                throw InsufficientStockException(
                    productId = product.id!!,
                    requested = item.quantity,
                    available = product.stock
                )
            }

            products[product.id!!] = product
        }

        return products.toMap()
    }

    /**
     * 재고 차감 (비즈니스 정책: 주문 생성 시점에 즉시 차감)
     *
     * 비관적 락을 사용하여 동시성을 제어합니다.
     * 데드락 방지를 위해 productId를 정렬하여 조회합니다. (findAllByIdWithLock에서 ORDER BY)
     * Product 도메인 엔티티의 deductStock() 메서드를 사용하여 재고를 차감합니다.
     *
     * 주의: 비관적 락으로 조회한 엔티티는 명시적으로 save()를 호출하지 않고
     * JPA의 더티 체킹(Dirty Checking)으로 자동 저장되도록 합니다.
     *
     * @return 조회된 상품 Map (productId -> Product)
     */
    private fun deductStock(items: List<OrderItemCommand>): Map<UUID, Product> {
        // 비관적 락으로 상품 조회 (데드락 방지를 위해 ID 정렬됨)
        val productIds = items.map { it.productId }.distinct().sorted()
        val lockedProducts = productService.findAllByIdWithLock(productIds)

        val products = mutableMapOf<UUID, Product>()

        // 재고 차감 (더티 체킹으로 자동 저장됨)
        items.forEach { item ->
            val product = lockedProducts.find { it.id == item.productId }
                ?: throw ProductNotFoundException(item.productId)

            // deductStock()에서 재고 검증 및 차감
            product.deductStock(item.quantity)
            products[product.id!!] = product
        }

        return products.toMap()
    }

    /**
     * 쿠폰 검증 및 사용 처리
     * UserCoupon 도메인 엔티티의 use() 메서드를 사용하여 쿠폰을 사용합니다.
     */
    private fun validateAndUseCoupon(couponId: UUID, userId: UUID): UserCoupon {
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
    private fun calculateTotalAmount(items: List<OrderItemCommand>, products: Map<UUID, Product>): Long {
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
     * 비관적 락을 사용하여 동시성을 제어합니다.
     * Product 도메인 엔티티의 restoreStock() 메서드를 사용하여 재고를 복원합니다.
     *
     * 주의: 비관적 락으로 조회한 엔티티는 명시적으로 save()를 호출하지 않고
     * JPA의 더티 체킹(Dirty Checking)으로 자동 저장되도록 합니다.
     */
    private fun restoreStock(items: List<OrderItem>): List<RestoredStockItemDto> {
        // 비관적 락으로 상품 조회 (데드락 방지를 위해 ID 정렬됨)
        val productIds = items.map { it.productId }.distinct().sorted()
        val lockedProducts = productService.findAllByIdWithLock(productIds)

        // 재고 복원 (더티 체킹으로 자동 저장됨)
        return items.map { item ->
            val product = lockedProducts.find { it.id == item.productId }
                ?: throw ProductNotFoundException(item.productId)

            product.restoreStock(item.quantity)  // 도메인 메서드 사용
            // save()를 호출하지 않음 - 트랜잭션 종료 시 더티 체킹으로 자동 저장

            RestoredStockItemDto(
                productId = item.productId,
                quantity = item.quantity
            )
        }
    }

    /**
     * 쿠폰 복원 (만료되지 않은 경우만)
     * UserCoupon 도메인 엔티티의 restore() 메서드를 사용하여 쿠폰을 복원합니다.
     *
     * 비관적 락을 사용하여 동시성을 제어합니다.
     */
    private fun restoreCoupon(couponId: UUID, userId: UUID): RestoredCouponInfoDto {
        // 비관적 락으로 쿠폰 조회
        couponService.findByIdWithLock(couponId)

        // 사용자 쿠폰 조회 및 복원
        val userCoupon = couponService.findUserCoupon(userId, couponId)
        userCoupon.restore()  // 도메인 메서드 사용 (만료 체크 포함)
        couponService.updateUserCoupon(userCoupon)

        return RestoredCouponInfoDto(
            couponId = couponId,
            status = userCoupon.status.name
        )
    }

    private fun generateOrderNumber(): String {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val uuid = UUID.randomUUID().toString().substring(0, 8).uppercase()
        return "ORD-$dateStr-$uuid"
    }
}