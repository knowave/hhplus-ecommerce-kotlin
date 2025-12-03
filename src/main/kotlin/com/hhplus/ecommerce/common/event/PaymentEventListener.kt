package com.hhplus.ecommerce.common.event

import com.hhplus.ecommerce.application.payment.DataPlatformService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 결제 이벤트 리스너
 *
 * 결제 관련 이벤트를 비동기로 처리합니다.
 * 트랜잭션 커밋 후에만 실행되어 데이터 정합성을 보장합니다.
 */
@Component
class PaymentEventListener(
    private val dataPlatformService: DataPlatformService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 결제 완료 이벤트 처리 (비동기)
     *
     * 데이터 플랫폼 전송
     */
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        logger.info("결제 완료 이벤트 수신 (Async/AfterCommit) - paymentId: ${event.paymentId}, orderId: ${event.orderId}")

        // 데이터 플랫폼 전송 (비동기)
        sendToDataPlatform(event)
    }

    /**
     * 데이터 플랫폼 전송 (비동기)
     */
    private fun sendToDataPlatform(event: PaymentCompletedEvent) {
        try {
            dataPlatformService.sendPaymentData(event)
            logger.info("데이터 플랫폼 전송 요청 완료 - orderId: ${event.orderId}")
        } catch (e: Exception) {
            logger.error("데이터 플랫폼 전송 요청 실패 - orderId: ${event.orderId}, error: ${e.message}", e)
        }
    }
}

