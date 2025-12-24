package com.hhplus.ecommerce.infrastructure.kafka

import com.hhplus.ecommerce.common.event.CouponIssuedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * 쿠폰 발급 이벤트를 Kafka로 발행하는 Producer
 *
 * CouponIssuedEvent를 Kafka 토픽으로 전송하여 Consumer가 비동기적으로 처리합니다.
 * - 사용자 알림 발송
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Profile("!load-test")
class CouponEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TOPIC_COUPON_ISSUED = "coupon-issued"
    }

    /**
     * 쿠폰 발급 이벤트를 Kafka로 발행
     *
     * @param event 쿠폰 발급 이벤트
     * @return CompletableFuture<SendResult> 전송 결과
     */
    fun sendCouponIssuedEvent(event: CouponIssuedEvent): CompletableFuture<SendResult<String, Any>> {
        val key = event.userCouponId.toString()

        logger.info("Kafka Producer - 쿠폰 발급 이벤트 발행 시작: userCouponId={}, userId={}, couponId={}",
            event.userCouponId, event.userId, event.couponId)

        return kafkaTemplate.send(TOPIC_COUPON_ISSUED, key, event).apply {
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
                        TOPIC_COUPON_ISSUED,
                        key,
                        ex.message,
                        ex
                    )
                }
            }
        }
    }
}