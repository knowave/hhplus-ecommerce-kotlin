package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.LockManager
import com.hhplus.ecommerce.domain.coupon.*
import com.hhplus.ecommerce.domain.payment.PaymentRepository
import com.hhplus.ecommerce.domain.order.entity.*
import com.hhplus.ecommerce.presentation.payment.dto.*
import com.hhplus.ecommerce.domain.payment.entity.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 결제 서비스 구현체
 *
 * 비즈니스 정책에 따른 결제 처리:
 * 1. 결제 성공: 잔액 차감 → 주문 상태 변경(PAID) → 데이터 전송 레코드 생성(PENDING)
 * 2. 결제 실패: 재고 복원 → 쿠폰 복원 → 주문 취소(CANCELLED)
 */
@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val lockManager: LockManager
) : PaymentService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun processPayment(orderId: Long, request: ProcessPaymentRequest): ProcessPaymentResponse {
        // 1. 주문 조회 및 검증
        val order = orderService.getOrder(orderId)

        // 권한 확인
        if (order.userId != request.userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        // 주문 상태 확인 (PENDING만 결제 가능)
        if (order.status != OrderStatus.PENDING) {
            throw InvalidOrderStatusException(orderId, order.status.name)
        }

        // 이미 결제된 주문인지 확인
        val existingPayment = paymentRepository.findByOrderId(orderId)
        if (existingPayment != null) {
            throw AlreadyPaidException(orderId)
        }

        // 2. 잔액 차감 (User Lock 사용)
        val paymentAmount = order.finalAmount
        val (previousBalance, currentBalance) = lockManager.executeWithUserLock(request.userId) {
            val user = userService.getUser(request.userId)
            val previousBalance = user.balance

            // 잔액 차감 (User 도메인 메서드 사용)
            try {
                user.deduct(paymentAmount)  // 도메인 메서드 사용 (검증 포함)
                userService.updateUser(user)
                Pair(previousBalance, user.balance)
            } catch (e: InsufficientBalanceException) {
                // 결제 실패 시 보상 트랜잭션
                handlePaymentFailure(order)
                throw e
            }
        }

        // 5. 주문 상태 변경 (PENDING → PAID) - 도메인 메서드 사용
        order.markAsPaid()
        orderService.updateOrder(order)

        // 6. 결제 레코드 생성
        val now = LocalDateTime.now()
        val payment = Payment(
            paymentId = paymentRepository.generateId(),
            orderId = orderId,
            userId = request.userId,
            amount = paymentAmount,
            status = PaymentStatus.SUCCESS,
            paidAt = now
        )
        paymentRepository.save(payment)

        // 7. 데이터 전송 레코드 생성 (Outbox Pattern)
        val transmission = DataTransmission(
            transmissionId = paymentRepository.generateTransmissionId(),
            orderId = orderId,
            status = TransmissionStatus.PENDING,
            attempts = 0,
            createdAt = now,
            nextRetryAt = now.plusMinutes(1) // 1분 후 첫 전송 시도
        )
        paymentRepository.saveTransmission(transmission)

        // 8. 응답 생성
        return ProcessPaymentResponse(
            paymentId = payment.paymentId,
            orderId = order.id,
            orderNumber = order.orderNumber,
            userId = order.userId,
            amount = paymentAmount,
            paymentStatus = payment.status.name,
            orderStatus = order.status.name,
            balance = BalanceInfo(
                previousBalance = previousBalance,
                paidAmount = paymentAmount,
                remainingBalance = currentBalance
            ),
            dataTransmission = DataTransmissionInfo(
                transmissionId = transmission.transmissionId,
                status = transmission.status.name,
                scheduledAt = transmission.nextRetryAt!!.format(DATE_FORMATTER)
            ),
            paidAt = payment.paidAt.format(DATE_FORMATTER)
        )
    }

    override fun getPaymentDetail(paymentId: Long, userId: Long): PaymentDetailResponse {
        val payment = paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)

        // 권한 확인
        if (payment.userId != userId) {
            throw ForbiddenException("다른 사용자의 결제 정보입니다.")
        }

        val order = orderService.getOrder(payment.orderId)
        val transmission = paymentRepository.findTransmissionByOrderId(payment.orderId)

        return PaymentDetailResponse(
            paymentId = payment.paymentId,
            orderId = payment.orderId,
            orderNumber = order.orderNumber,
            userId = payment.userId,
            amount = payment.amount,
            paymentStatus = payment.status.name,
            paidAt = payment.paidAt.format(DATE_FORMATTER),
            dataTransmission = DataTransmissionDetailInfo(
                transmissionId = transmission?.transmissionId ?: 0L,
                status = transmission?.status?.name ?: "UNKNOWN",
                sentAt = transmission?.sentAt?.format(DATE_FORMATTER),
                attempts = transmission?.attempts ?: 0
            )
        )
    }

    override fun getOrderPayment(orderId: Long, userId: Long): OrderPaymentResponse {
        val order = orderService.getOrder(orderId)

        // 권한 확인
        if (order.userId != userId) {
            throw ForbiddenException("다른 사용자의 주문입니다.")
        }

        val payment = paymentRepository.findByOrderId(orderId)

        return OrderPaymentResponse(
            orderId = orderId,
            orderNumber = order.orderNumber,
            payment = payment?.let {
                PaymentInfo(
                    paymentId = it.paymentId,
                    amount = it.amount,
                    status = it.status.name,
                    paidAt = it.paidAt.format(DATE_FORMATTER)
                )
            }
        )
    }

    override fun getTransmissionDetail(transmissionId: Long): TransmissionDetailResponse {
        val transmission = paymentRepository.findTransmissionById(transmissionId)
            ?: throw TransmissionNotFoundException(transmissionId)

        val order = orderService.getOrder(transmission.orderId)

        return TransmissionDetailResponse(
            transmissionId = transmission.transmissionId,
            orderId = transmission.orderId,
            orderNumber = order.orderNumber,
            status = transmission.status.name,
            attempts = transmission.attempts,
            maxAttempts = transmission.maxAttempts,
            createdAt = transmission.createdAt.format(DATE_FORMATTER),
            sentAt = transmission.sentAt?.format(DATE_FORMATTER),
            nextRetryAt = transmission.nextRetryAt?.format(DATE_FORMATTER),
            errorMessage = transmission.errorMessage
        )
    }

    override fun retryTransmission(transmissionId: Long): RetryTransmissionResponse {
        val transmission = paymentRepository.findTransmissionById(transmissionId)
            ?: throw TransmissionNotFoundException(transmissionId)

        // 이미 성공한 전송은 재시도 불가
        if (transmission.status == TransmissionStatus.SUCCESS) {
            throw AlreadySuccessException(transmissionId)
        }

        // 재시도 시도 (실제로는 외부 시스템 호출)
        // Mock에서는 항상 성공으로 처리
        val now = LocalDateTime.now()
        transmission.status = TransmissionStatus.SUCCESS
        transmission.sentAt = now
        transmission.attempts += 1
        transmission.nextRetryAt = null
        transmission.errorMessage = null

        paymentRepository.saveTransmission(transmission)

        return RetryTransmissionResponse(
            transmissionId = transmission.transmissionId,
            status = transmission.status.name,
            retriedAt = now.format(DATE_FORMATTER),
            attempts = transmission.attempts
        )
    }

    /**
     * 결제 취소
     *
     * 단일 책임 원칙에 따라 결제 도메인의 책임만 수행:
     * 1. 결제 상태 변경 (SUCCESS → CANCELLED)
     * 2. 사용자 잔액 환불
     *
     * 주문 상태 변경, 재고 복구, 쿠폰 복구는 OrderService의 책임입니다.
     */
    override fun cancelPayment(paymentId: Long, request: com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentRequest): com.hhplus.ecommerce.presentation.payment.dto.CancelPaymentResponse {
        // 1. 결제 조회 및 검증
        val payment = paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)

        // 권한 확인
        if (payment.userId != request.userId) {
            throw ForbiddenException("다른 사용자의 결제입니다.")
        }

        // 이미 취소된 결제인지 확인
        if (payment.status == PaymentStatus.CANCELLED) {
            throw AlreadyCancelledException(paymentId)
        }

        // 결제 성공 상태가 아니면 취소 불가
        if (payment.status != PaymentStatus.SUCCESS) {
            throw InvalidPaymentStatusException(paymentId, payment.status.name)
        }

        // 2. 주문 조회 (응답에만 사용)
        val order = orderService.getOrder(payment.orderId)

        // 3. 잔액 환불 (User Lock 사용)
        val refundAmount = payment.amount
        val (previousBalance, currentBalance) = lockManager.executeWithUserLock(request.userId) {
            val user = userService.getUser(request.userId)

            val previousBalance = user.balance
            user.refund(refundAmount)  // User 도메인 메서드 사용
            userService.updateUser(user)

            Pair(previousBalance, user.balance)
        }

        // 4. 결제 상태 변경 (SUCCESS → CANCELLED)
        val now = LocalDateTime.now()
        val cancelledPayment = payment.copy(
            status = PaymentStatus.CANCELLED
        )
        paymentRepository.save(cancelledPayment)

        // 5. 응답 생성 (주문 상태는 변경하지 않음)
        return CancelPaymentResponse(
            paymentId = payment.paymentId,
            orderId = order.id,
            orderNumber = order.orderNumber,
            userId = request.userId,
            refundedAmount = refundAmount,
            paymentStatus = cancelledPayment.status.name,
            orderStatus = order.status.name,  // 현재 주문 상태만 반환
            balance = RefundBalanceInfo(
                previousBalance = previousBalance,
                refundedAmount = refundAmount,
                currentBalance = currentBalance
            ),
            cancelledAt = now.format(DATE_FORMATTER)
        )
    }

    /**
     * 결제 실패 시 보상 트랜잭션 처리
     * 비즈니스 정책: 재고 복원 → 쿠폰 복원 → 주문 취소
     *
     * Product Lock을 사용하여 재고 복원의 동시성을 제어합니다.
     * 각 도메인 엔티티의 비즈니스 로직 메서드를 사용합니다.
     */
    private fun handlePaymentFailure(order: Order) {
        // 1. 재고 복원 (Product Lock 사용, 도메인 메서드 사용)
        val productIds = order.items.map { it.productId }
        lockManager.executeWithProductLocks(productIds) {
            order.items.forEach { item ->
                val product = productService.findProductById(item.productId)
                product.restoreStock(item.quantity)  // 도메인 메서드 사용
                productService.updateProduct(product)
            }
        }

        // 2. 쿠폰 복원 (도메인 메서드 사용)
        if (order.appliedCouponId != null) {
            val userCoupon = couponService.findUserCoupon(order.userId, order.appliedCouponId)
            if (userCoupon.status == CouponStatus.USED) {
                lockManager.executeWithCouponLock(order.appliedCouponId) {
                    userCoupon.restore()  // 도메인 메서드 사용 (만료 체크 포함)
                    couponService.updateUserCoupon(userCoupon)
                }
            }
        }

        // 3. 주문 취소 - 도메인 메서드 사용
        order.cancel()
        orderService.updateOrder(order)
    }
}