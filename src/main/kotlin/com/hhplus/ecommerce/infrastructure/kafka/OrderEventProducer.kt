package com.hhplus.ecommerce.infrastructure.kafka

import com.hhplus.ecommerce.common.event.OrderCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import java.util.concurrent.CompletableFuture

/**
 * 주문 생성 이벤트를 Kafka로 발행하는 Producer
 *
 * OrderCreatedEvent를 Kafka 토픽으로 전송하여 Consumer가 비동기적으로 처리합니다.
 * - 상품 랭킹 업데이트
 * - 카트 삭제
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Profile("!load-test")
class OrderEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TOPIC_ORDER_CREATED = "order-created"
    }

    /**
     * 주문 생성 이벤트를 Kafka로 발행
     *
     * @param event 주문 생성 이벤트
     * @return CompletableFuture<SendResult> 전송 결과
     */
    fun sendOrderCreatedEvent(event: OrderCreatedEvent): CompletableFuture<SendResult<String, Any>> {
        val key = event.orderId.toString()

        logger.info("Kafka Producer - 주문 생성 이벤트 발행 시작: orderId={}, userId={}",
            event.orderId, event.userId)

        return kafkaTemplate.send(TOPIC_ORDER_CREATED, key, event).apply {
            // 성공/실패 콜백
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
                        TOPIC_ORDER_CREATED,
                        key,
                        ex.message,
                        ex
                    )
                }
            }
        }
    }
}