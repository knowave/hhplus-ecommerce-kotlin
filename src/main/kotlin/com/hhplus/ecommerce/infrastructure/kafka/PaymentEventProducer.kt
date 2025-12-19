package com.hhplus.ecommerce.infrastructure.kafka

import com.hhplus.ecommerce.common.event.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * 결제 완료 이벤트를 Kafka로 발행하는 Producer
 * PaymentCompletedEvent를 Kafka 토픽으로 전송하여 Consumer가 비동기적으로 처리.
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PaymentEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TOPIC_PAYMENT_COMPLETED = "payment-completed"
    }

    /**
     * 결제 완료 이벤트를 Kafka로 발행.
     *
     * @param event 결제 완료 이벤트
     * @return CompletableFuture<SendResult> 전송 결과
     */
    fun sendPaymentCompletedEvent(event: PaymentCompletedEvent): CompletableFuture<SendResult<String, Any>> {
        val key = event.orderId.toString()

        logger.info("Kafka Producer - 결제 완료 이벤트 발행 시작: orderId={}, paymentId={}",
            event.orderId, event.paymentId)

        return kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, key, event).apply {
            // 성공 콜백
            whenComplete { result, ex ->
                if (ex == null) {
                    logger.info(
                        "Kafka Producer - 메시지 전송 성공: topic={}, partition={}, offset={}, key={}",
                        result?.recordMetadata?.topic(),
                        result?.recordMetadata?.partition(),
                        result?.recordMetadata?.offset(),
                        key
                    )
                } else {
                    logger.error(
                        "Kafka Producer - 메시지 전송 실패: topic={}, key={}, error={}",
                        TOPIC_PAYMENT_COMPLETED,
                        key,
                        ex.message,
                        ex
                    )
                }
            }
        }
    }
}