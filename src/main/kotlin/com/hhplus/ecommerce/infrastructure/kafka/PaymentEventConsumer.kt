package com.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.hhplus.ecommerce.application.payment.DataPlatformService
import com.hhplus.ecommerce.common.event.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * 결제 완료 이벤트를 Kafka에서 수신하는 Consumer
 *
 * Kafka에서 PaymentCompletedEvent를 수신하여
 * 데이터 플랫폼으로 전송하는 비동기 작업을 처리.
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PaymentEventConsumer(
    private val dataPlatformService: DataPlatformService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * payment-completed 토픽의 메시지를 소비
     * ErrorHandler가 자동으로 재시도 및 DLQ 처리를 수행
     *
     * @param message 메시지 페이로드 (JSON → PaymentCompletedEvent로 역직렬화)
     * @param partition 파티션 번호
     * @param offset offset 위치
     */
    @KafkaListener(
        topics = [PaymentEventProducer.TOPIC_PAYMENT_COMPLETED],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Profile("!load-test")
    fun consumePaymentCompletedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        logger.info(
            "Kafka Consumer - 메시지 수신: topic={}, partition={}, offset={}",
            PaymentEventProducer.TOPIC_PAYMENT_COMPLETED,
            partition,
            offset
        )

        // JSON 문자열을 PaymentCompletedEvent로 역직렬화
        val event = objectMapper.readValue(message, PaymentCompletedEvent::class.java)

        logger.info(
            "Kafka Consumer - 결제 완료 이벤트 처리 시작: orderId={}, paymentId={}",
            event.orderId,
            event.paymentId
        )

        // 데이터 플랫폼으로 전송 (기존 mockAPI 호출 로직 사용)
        dataPlatformService.sendPaymentData(event)

        logger.info(
            "Kafka Consumer - 메시지 처리 완료: orderId={}, partition={}, offset={}",
            event.orderId,
            partition,
            offset
        )
        // ErrorHandler가 자동으로 offset 커밋 및 예외 발생 시 재시도/DLQ 처리
    }
}