package com.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.hhplus.ecommerce.common.event.CouponIssuedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * 쿠폰 발급 이벤트를 Kafka에서 수신하는 Consumer
 *
 * Kafka에서 CouponIssuedEvent를 수신하여 다음 작업을 비동기로 처리합니다:
 * 1. 사용자 알림 발송 (쿠폰 발급 완료 알림)
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class CouponEventConsumer(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * coupon-issued 토픽의 메시지를 소비
     * ErrorHandler가 자동으로 재시도 및 DLQ 처리를 수행
     *
     * @param message 메시지 페이로드 (JSON → CouponIssuedEvent로 역직렬화)
     * @param partition 파티션 번호
     * @param offset offset 위치
     */
    @KafkaListener(
        topics = [CouponEventProducer.TOPIC_COUPON_ISSUED],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeCouponIssuedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        logger.info(
            "Kafka Consumer - 메시지 수신: topic={}, partition={}, offset={}",
            CouponEventProducer.TOPIC_COUPON_ISSUED,
            partition,
            offset
        )

        // JSON 문자열을 CouponIssuedEvent로 역직렬화
        val event = objectMapper.readValue(message, CouponIssuedEvent::class.java)

        logger.info(
            "Kafka Consumer - 쿠폰 발급 이벤트 처리 시작: userCouponId={}, userId={}, couponId={}",
            event.userCouponId,
            event.userId,
            event.couponId
        )

        // 사용자 알림 발송
        sendNotificationToUser(event)

        logger.info(
            "Kafka Consumer - 메시지 처리 완료: userCouponId={}, partition={}, offset={}",
            event.userCouponId,
            partition,
            offset
        )
        // ErrorHandler가 자동으로 offset 커밋 및 예외 발생 시 재시도/DLQ 처리
    }

    /**
     * 사용자 알림 발송 (비동기)
     * 쿠폰 발급 완료 알림을 사용자에게 전송합니다.
     *
     * 현재는 로깅만 수행하며, 향후 실제 알림 서비스(Push, Email 등)를 연동.
     */
    private fun sendNotificationToUser(event: CouponIssuedEvent) {
        try {
            // notificationService.sendCouponIssuedNotification(event.userId, event.couponName, event.expiresAt)

            logger.info(
                "사용자 알림 발송 (로깅) - userId: {}, couponName: '{}', discountRate: {}%, expiresAt: {}",
                event.userId,
                event.couponName,
                event.discountRate,
                event.expiresAt
            )
            logger.info("쿠폰 발급 완료! '{}'님께 '{}' 쿠폰이 발급되었습니다. ({}% 할인, 만료일: {})",
                event.userId,
                event.couponName,
                event.discountRate,
                event.expiresAt
            )
        } catch (e: Exception) {
            logger.error("사용자 알림 발송 실패 - userId: ${event.userId}, error: ${e.message}", e)
            // 알림 발송 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}