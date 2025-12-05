package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.common.event.PaymentCompletedEvent
import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import com.hhplus.ecommerce.domain.payment.entity.TransmissionStatus
import com.hhplus.ecommerce.domain.payment.repository.DataTransmissionJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class DataPlatformServiceImpl(
    private val dataTransmissionRepository: DataTransmissionJpaRepository
) : DataPlatformService {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 결제 완료 후 데이터 플랫폼으로 전송합니다.
     * 
     * 외부 API 호출을 시뮬레이션하며, 결과를 DataTransmission 엔티티에 기록합니다.
     * 실패 시 추후 배치를 통해 재시도할 수 있도록 합니다 (Outbox Pattern).
     * 
     * 별도의 트랜잭션으로 실행하여 원본 결제 트랜잭션에 영향을 주지 않습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun sendPaymentData(event: PaymentCompletedEvent) {
        val orderId = event.orderId
        logger.info("데이터 플랫폼 전송 시작 - orderId: $orderId, paymentId: ${event.paymentId}")

        try {
            // 외부 API 호출 시뮬레이션 (Mock)
            val isSuccess = mockExternalApiCall(event)
            
            if (isSuccess) {
                logger.info("데이터 플랫폼 전송 성공 - orderId: $orderId")
                saveTransmissionLog(orderId, TransmissionStatus.SUCCESS, "Success")
            } else {
                logger.warn("데이터 플랫폼 전송 실패 (Mock) - orderId: $orderId")
                saveTransmissionLog(orderId, TransmissionStatus.FAILED, "Mock API returned false")
            }
        } catch (e: Exception) {
            logger.error("데이터 플랫폼 전송 중 예외 발생 - orderId: $orderId, error: ${e.message}", e)
            saveTransmissionLog(orderId, TransmissionStatus.FAILED, e.message)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mockExternalApiCall(event: PaymentCompletedEvent): Boolean {
        // 20% 확률로 전송 실패 시뮬레이션
        return Math.random() > 0.2
    }

    private fun saveTransmissionLog(orderId: UUID, status: TransmissionStatus, message: String?) {
        val transmission = DataTransmission(
            orderId = orderId,
            status = status,
            errorMessage = message,
            attempts = 1,
            maxAttempts = 3,
            sentAt = if (status == TransmissionStatus.SUCCESS) LocalDateTime.now() else null,
            nextRetryAt = if (status == TransmissionStatus.FAILED) LocalDateTime.now().plusMinutes(5) else null
        )
        dataTransmissionRepository.save(transmission)
    }
}
