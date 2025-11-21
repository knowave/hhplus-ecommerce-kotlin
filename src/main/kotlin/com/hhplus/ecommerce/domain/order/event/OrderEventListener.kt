package com.hhplus.ecommerce.domain.order.event

import com.hhplus.ecommerce.application.cart.CartService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 주문 이벤트 리스너
 *
 * 주문 관련 이벤트를 비동기로 처리합니다.
 */
@Component
class OrderEventListener(
    private val cartService: CartService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 생성 이벤트 처리
     *
     * 1. 카트 삭제 (비동기)
     * 2. 향후 알림 발송, 통계 업데이트 등 추가 가능
     */
    @Async("taskExecutor")
    @EventListener
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info("주문 생성 이벤트 수신 - orderId: ${event.orderId}, userId: ${event.userId}")

        // 1. 카트 삭제
        try {
            cartService.deleteCarts(event.userId, event.productIds)
            logger.info("카트 삭제 성공 - userId: ${event.userId}, productIds: ${event.productIds}")
        } catch (e: Exception) {
            // 카트 삭제 실패해도 주문은 유효함
            logger.error("카트 삭제 실패 - userId: ${event.userId}, error: ${e.message}", e)
        }

        // 2. 향후 추가 가능한 작업들
        // - 주문 확인 알림 발송
        // - 판매 통계 업데이트
        // - 추천 시스템 업데이트
    }
}
