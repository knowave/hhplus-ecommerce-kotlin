package com.hhplus.ecommerce.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.product.ProductRankingService
import com.hhplus.ecommerce.common.event.OrderCreatedEvent
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * 주문 생성 이벤트를 Kafka에서 수신하는 Consumer
 *
 * Kafka에서 OrderCreatedEvent를 수신하여 다음 작업을 비동기로 처리합니다:
 * 1. 상품 랭킹 업데이트 (일간, 주간)
 * 2. 카트 삭제
 *
 * 기존 OrderEventListener의 로직을 Kafka 기반으로 전환한 버전입니다.
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class OrderEventConsumer(
    private val cartService: CartService,
    private val productRankingService: ProductRankingService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * order-created 토픽의 메시지를 소비
     * ErrorHandler가 자동으로 재시도 및 DLQ 처리를 수행
     *
     * @param message 메시지 페이로드 (JSON → OrderCreatedEvent로 역직렬화)
     * @param partition 파티션 번호
     * @param offset offset 위치
     */
    @KafkaListener(
        topics = [OrderEventProducer.TOPIC_ORDER_CREATED],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeOrderCreatedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        logger.info(
            "Kafka Consumer - 메시지 수신: topic={}, partition={}, offset={}",
            OrderEventProducer.TOPIC_ORDER_CREATED,
            partition,
            offset
        )

        // JSON 문자열을 OrderCreatedEvent로 역직렬화
        val event = objectMapper.readValue(message, OrderCreatedEvent::class.java)

        logger.info(
            "Kafka Consumer - 주문 생성 이벤트 처리 시작: orderId={}, userId={}",
            event.orderId,
            event.userId
        )

        // 카트 삭제
        deleteUserCart(event)

        // 상품 랭킹 업데이트
        updateProductRanking(event)

        logger.info(
            "Kafka Consumer - 메시지 처리 완료: orderId={}, partition={}, offset={}",
            event.orderId,
            partition,
            offset
        )
        // ErrorHandler가 자동으로 offset 커밋 및 예외 발생 시 재시도/DLQ 처리
    }

    /**
     * 상품 랭킹 업데이트 (비동기)
     * 일간 및 주간 랭킹을 업데이트합니다.
     */
    private fun updateProductRanking(event: OrderCreatedEvent) {
        try {
            event.items.forEach { item ->
                // 일간 랭킹 업데이트
                productRankingService.incrementOrderCount(
                    productId = item.productId,
                    quantity = item.quantity,
                    period = RankingPeriod.DAILY
                )

                // 주간 랭킹 업데이트
                productRankingService.incrementOrderCount(
                    productId = item.productId,
                    quantity = item.quantity,
                    period = RankingPeriod.WEEKLY
                )
            }
            logger.debug("상품 랭킹 업데이트 완료 - orderId: {}", event.orderId)
        } catch (e: Exception) {
            logger.error("상품 랭킹 업데이트 실패 - orderId: ${event.orderId}, error: ${e.message}", e)
            // 랭킹 업데이트 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }

    /**
     * 카트 삭제 (비동기)
     * 주문한 상품들을 사용자의 카트에서 삭제합니다.
     */
    private fun deleteUserCart(event: OrderCreatedEvent) {
        try {
            cartService.deleteCarts(event.userId, event.productIds)
            logger.info("카트 삭제 성공 - userId: ${event.userId}")
        } catch (e: Exception) {
            logger.error("카트 삭제 실패 - userId: ${event.userId}, error: ${e.message}", e)
            // 카트 삭제 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}