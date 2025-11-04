package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.common.exception.AlreadyPaidException
import com.hhplus.ecommerce.common.exception.AlreadySuccessException
import com.hhplus.ecommerce.common.exception.ForbiddenException
import com.hhplus.ecommerce.common.exception.InsufficientBalanceException
import com.hhplus.ecommerce.common.exception.InvalidOrderStatusException
import com.hhplus.ecommerce.common.exception.OrderNotFoundException
import com.hhplus.ecommerce.common.exception.PaymentNotFoundException
import com.hhplus.ecommerce.common.exception.TransmissionNotFoundException
import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.coupon.CouponRepository
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.domain.order.OrderRepository
import com.hhplus.ecommerce.domain.payment.PaymentRepository
import com.hhplus.ecommerce.domain.product.ProductRepository
import com.hhplus.ecommerce.domain.user.UserRepository
import com.hhplus.ecommerce.domain.order.entity.Order
import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import com.hhplus.ecommerce.presentation.payment.dto.BalanceInfo
import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.presentation.payment.dto.DataTransmissionDetailInfo
import com.hhplus.ecommerce.presentation.payment.dto.DataTransmissionInfo
import com.hhplus.ecommerce.presentation.payment.dto.OrderPaymentResponse
import com.hhplus.ecommerce.domain.payment.entity.Payment
import com.hhplus.ecommerce.presentation.payment.dto.PaymentDetailResponse
import com.hhplus.ecommerce.presentation.payment.dto.PaymentInfo
import com.hhplus.ecommerce.domain.payment.entity.PaymentStatus
import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentRequest
import com.hhplus.ecommerce.presentation.payment.dto.ProcessPaymentResponse
import com.hhplus.ecommerce.presentation.payment.dto.RetryTransmissionResponse
import com.hhplus.ecommerce.presentation.payment.dto.TransmissionDetailResponse
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
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
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val couponRepository: CouponRepository
) : PaymentService {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    override fun processPayment(orderId: Long, request: ProcessPaymentRequest): ProcessPaymentResponse {
        // 1. 주문 조회 및 검증
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

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

        // 2. 사용자 조회
        val user = userRepository.findById(request.userId)
            ?: throw UserNotFoundException(request.userId)

        // 3. 잔액 확인
        val paymentAmount = order.finalAmount
        if (user.balance < paymentAmount) {
            // 결제 실패 시 보상 트랜잭션
            handlePaymentFailure(order)
            throw InsufficientBalanceException(
                required = paymentAmount,
                available = user.balance
            )
        }

        // 4. 잔액 차감
        val previousBalance = user.balance
        user.balance -= paymentAmount
        userRepository.save(user)

        // 5. 주문 상태 변경 (PENDING → PAID) - 도메인 메서드 사용
        order.markAsPaid()
        orderRepository.save(order)

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
                remainingBalance = user.balance
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

        val order = orderRepository.findById(payment.orderId)
            ?: throw OrderNotFoundException(payment.orderId)

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
        val order = orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId)

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

        val order = orderRepository.findById(transmission.orderId)
            ?: throw OrderNotFoundException(transmission.orderId)

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
     * 결제 실패 시 보상 트랜잭션 처리
     * 비즈니스 정책: 재고 복원 → 쿠폰 복원 → 주문 취소
     */
    private fun handlePaymentFailure(order: Order) {
        // 1. 재고 복원
        order.items.forEach { item ->
            val product = productRepository.findById(item.productId)
            if (product != null) {
                product.stock += item.quantity
                productRepository.save(product)
            }
        }

        // 2. 쿠폰 복원
        if (order.appliedCouponId != null) {
            val userCoupon = couponRepository.findUserCoupon(order.userId, order.appliedCouponId)
            if (userCoupon != null && userCoupon.status == CouponStatus.USED) {
                // 만료되지 않은 경우만 복원
                val expiresAt = LocalDateTime.parse(userCoupon.expiresAt, DATE_FORMATTER)
                if (expiresAt.isAfter(LocalDateTime.now())) {
                    userCoupon.status = CouponStatus.AVAILABLE
                    userCoupon.usedAt = null
                    couponRepository.saveUserCoupon(userCoupon)
                }
            }
        }

        // 3. 주문 취소 - 도메인 메서드 사용
        order.cancel()
        orderRepository.save(order)
    }
}