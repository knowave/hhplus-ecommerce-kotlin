package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.order.OrderService
import com.hhplus.ecommerce.application.payment.dto.*
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.shipping.ShippingService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.common.event.PaymentCompletedEvent
import com.hhplus.ecommerce.common.exception.*
import com.hhplus.ecommerce.common.lock.DistributedLock
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.order.entity.*
import com.hhplus.ecommerce.domain.payment.entity.*
import com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
import com.hhplus.ecommerce.domain.payment.repository.PaymentJpaRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 결제 서비스 구현체
 *
 * 비즈니스 정책에 따른 결제 처리:
 * 1. 결제 성공: 잔액 차감 → 주문 상태 변경(PAID) → shipping 생성 (PENDING) → 결제 완료 이벤트 발행 (비동기 데이터 전송)
 * 2. 결제 실패: 재고 복원 → 쿠폰 복원 → 주문 취소(CANCELLED)
 */
@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentJpaRepository,
    private val transmissionRepository: DataTransmissionJpaRepository,
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val shippingService: ShippingService,
    private val applicationEventPublisher: ApplicationEventPublisher
) : PaymentService {
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * 결제 처리
     *
     * 동시성 제어:
     * - 분산락: 다중 서버 환경에서 같은 주문에 대한 동시 결제 방지
     * - 비관적 락: DB 레벨에서 주문 데이터의 원자성 보장
     *
     * 분산락 + 비관적 락을 함께 사용하는 이유:
     * - 분산락: Redis 레벨에서 빠른 동시성 제어 (다중 서버 환경)
     * - 비관적 락: DB 트랜잭션 내에서 데이터 정합성 보장
     */
    @DistributedLock(
        key = "'payment:process:' + #orderId",
        waitTimeMs = 5000,
        leaseTimeMs = 15000,
        errorMessage = "결제 처리 중입니다. 잠시 후 다시 시도해주세요.",
        unlockAfterCommit = true
    )
    @Transactional
    override fun processPayment(orderId: UUID, request: ProcessPaymentCommand): ProcessPaymentResult {
        // 주문 조회 및 검증 (비관적 락으로 동시성 제어)
        val order = orderService.getOrderWithLock(orderId)

        // 권한 확인
        order.validateOwner(request.userId)

        // 주문 상태 확인 (PENDING만 결제 가능)
        order.validatePaymentEligibility()

        // 이미 결제된 주문인지 확인
        val existingPayment = paymentRepository.findByOrderId(orderId)
        if (existingPayment != null) {
            throw AlreadyPaidException(orderId)
        }

        // 잔액 차감 (비관적 락 사용)
        val paymentAmount = order.finalAmount
        val (previousBalance, currentBalance) = try {
            // 비관적 락으로 사용자 조회
            val user = userService.findByIdWithLock(request.userId)

            val previousBalance = user.balance

            // 잔액 차감 (User 도메인 메서드 사용)
            user.deduct(paymentAmount)  // 도메인 메서드 사용 (검증 포함)
            userService.updateUser(user)

            Pair(previousBalance, user.balance)
        } catch (e: InsufficientBalanceException) {
            // 결제 실패 시 보상 트랜잭션
            handlePaymentFailure(order)
            throw e
        }

        // 주문 상태 변경 (PENDING → PAID) - 도메인 메서드 사용
        order.markAsPaid()
        orderService.updateOrder(order)

        // 결제 레코드 생성
        val now = LocalDateTime.now()
        val payment = Payment(
            orderId = orderId,
            userId = request.userId,
            amount = paymentAmount,
            status = PaymentStatus.SUCCESS,
            paidAt = now
        )
        val savedPayment = paymentRepository.save(payment)

        // 5. 배송 생성
        shippingService.createShipping(orderId, "CJ대한통운")

        // 6. 결제 완료 이벤트 발행 (비동기: 데이터 플랫폼 전송)
        applicationEventPublisher.publishEvent(
            PaymentCompletedEvent(
                paymentId = savedPayment.id!!,
                orderId = orderId,
                userId = request.userId,
                amount = paymentAmount
            )
        )

        // 7. 응답 생성
        return ProcessPaymentResult(
            paymentId = savedPayment.id!!,
            orderId = order.id!!,
            orderNumber = order.orderNumber,
            userId = order.userId,
            amount = paymentAmount,
            paymentStatus = savedPayment.status.name,
            orderStatus = order.status.name,
            balance = BalanceInfoResult(
                previousBalance = previousBalance,
                paidAmount = paymentAmount,
                remainingBalance = currentBalance
            ),
            dataTransmission = DataTransmissionInfoResult(
                transmissionId = null,
                status = "PENDING_EVENT_PROCESSING",
                scheduledAt = now.format(DATE_FORMATTER)
            ),
            paidAt = savedPayment.paidAt.format(DATE_FORMATTER)
        )
    }

    override fun getPaymentDetail(paymentId: UUID, userId: UUID): PaymentDetailResult {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        // 권한 확인
        if (payment.userId != userId) {
            throw ForbiddenException("access denied payment")
        }

        val order = orderService.getOrder(payment.orderId)
        val transmission = transmissionRepository.findByOrderId(payment.orderId)

        return PaymentDetailResult(
            paymentId = payment.id!!,
            orderId = payment.orderId,
            orderNumber = order.orderNumber,
            userId = payment.userId,
            amount = payment.amount,
            paymentStatus = payment.status.name,
            paidAt = payment.paidAt.format(DATE_FORMATTER),
            dataTransmission = DataTransmissionDetailInfoResult(
                transmissionId = transmission?.id.toString(),
                status = transmission?.status?.name ?: "UNKNOWN",
                sentAt = transmission?.sentAt?.format(DATE_FORMATTER),
                attempts = transmission?.attempts ?: 0
            )
        )
    }

    override fun getOrderPayment(orderId: UUID, userId: UUID): OrderPaymentResult {
        val order = orderService.getOrder(orderId)

        // 권한 확인
        if (order.userId != userId) {
            throw ForbiddenException("access denied order")
        }

        val payment = paymentRepository.findByOrderId(orderId)

        return OrderPaymentResult(
            orderId = orderId,
            orderNumber = order.orderNumber,
            payment = payment?.let {
                PaymentInfoResult(
                    paymentId = it.id!!,
                    amount = it.amount,
                    status = it.status.name,
                    paidAt = it.paidAt.format(DATE_FORMATTER)
                )
            }
        )
    }

    override fun getTransmissionDetail(transmissionId: UUID): TransmissionDetailResult {
        val transmission = transmissionRepository.findById(transmissionId)
            .orElseThrow{ TransmissionNotFoundException(transmissionId) }

        val order = orderService.getOrder(transmission.orderId)

        return TransmissionDetailResult(
            transmissionId = transmission.id!!,
            orderId = transmission.orderId,
            orderNumber = order.orderNumber,
            status = transmission.status.name,
            attempts = transmission.attempts,
            maxAttempts = transmission.maxAttempts,
            createdAt = transmission.createdAt!!.format(DATE_FORMATTER),
            sentAt = transmission.sentAt?.format(DATE_FORMATTER),
            nextRetryAt = transmission.nextRetryAt?.format(DATE_FORMATTER),
            errorMessage = transmission.errorMessage
        )
    }

    override fun retryTransmission(transmissionId: UUID): RetryTransmissionResult {
        val transmission = transmissionRepository.findById(transmissionId)
            .orElseThrow { TransmissionNotFoundException(transmissionId) }

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

        transmissionRepository.save(transmission)

        return RetryTransmissionResult(
            transmissionId = transmission.id!!,
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
    @DistributedLock(
        key = "'payment:cancel:' + #paymentId",
        waitTimeMs = 5000,
        leaseTimeMs = 15000,
        errorMessage = "결제 취소 처리 중입니다. 잠시 후 다시 시도해주세요.",
        unlockAfterCommit = true
    )
    @Transactional
    override fun cancelPayment(paymentId: UUID, request: CancelPaymentCommand): CancelPaymentResult {
        // 결제 조회 및 검증
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException(paymentId) }

        // 권한 확인
        if (payment.userId != request.userId) {
            throw ForbiddenException("access denied payment")
        }

        // 이미 취소된 결제인지 확인
        if (payment.status == PaymentStatus.CANCELLED) {
            throw AlreadyCancelledException(paymentId)
        }

        // 결제 성공 상태가 아니면 취소 불가
        if (payment.status != PaymentStatus.SUCCESS) {
            throw InvalidPaymentStatusException(paymentId, payment.status.name)
        }

        // 주문 조회 (응답에만 사용)
        val order = orderService.getOrder(payment.orderId)

        // 잔액 환불 (비관적 락 사용)
        val refundAmount = payment.amount

        // 비관적 락으로 사용자 조회
        val user = userService.findByIdWithLock(request.userId)

        val previousBalance = user.balance
        user.refund(refundAmount)  // User 도메인 메서드 사용
        userService.updateUser(user)

        val currentBalance = user.balance

        // 결제 상태 변경 (SUCCESS → CANCELLED)
        val now = LocalDateTime.now()
        payment.status = PaymentStatus.CANCELLED
        paymentRepository.save(payment)

        // 응답 생성 (주문 상태는 변경하지 않음)
        return CancelPaymentResult(
            paymentId = payment.id!!,
            orderId = order.id!!,
            orderNumber = order.orderNumber,
            userId = request.userId,
            refundedAmount = refundAmount,
            paymentStatus = payment.status.name,
            orderStatus = order.status.name,  // 현재 주문 상태만 반환
            balance = RefundBalanceInfoResult(
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
     * 비관적 락을 사용하여 재고 복원의 동시성을 제어합니다.
     * 각 도메인 엔티티의 비즈니스 로직 메서드를 사용합니다.
     */
    private fun handlePaymentFailure(order: Order) {
        // 재고 복원 (비관적 락 사용, 도메인 메서드 사용)
        val productIds = order.items.map { it.productId }.distinct().sorted()
        val lockedProducts = productService.findAllByIdWithLock(productIds)

        order.items.forEach { item ->
            val product = lockedProducts.find { it.id == item.productId }
                ?: throw ProductNotFoundException(item.productId)

            product.restoreStock(item.quantity)  // 도메인 메서드 사용
            productService.updateProduct(product)
        }

        // 쿠폰 복원 (비관적 락 사용, 도메인 메서드 사용)
        if (order.appliedCouponId != null) {
            val userCoupon = couponService.findUserCoupon(order.userId, order.appliedCouponId!!)
            if (userCoupon.status == CouponStatus.USED) {
                // 비관적 락으로 쿠폰 조회
                couponService.findByIdWithLock(order.appliedCouponId!!)

                userCoupon.restore()  // 도메인 메서드 사용 (만료 체크 포함)
                couponService.updateUserCoupon(userCoupon)
            }
        }

        // 3. 주문 취소 - 도메인 메서드 사용
        order.cancel()
        orderService.updateOrder(order)
    }
}