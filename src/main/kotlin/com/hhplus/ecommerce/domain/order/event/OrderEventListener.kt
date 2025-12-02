package com.hhplus.ecommerce.domain.order.event

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.product.ProductRankingService
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
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
    private val cartService: CartService,
    private val productRankingService: ProductRankingService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 생성 이벤트 처리
     *
     * 1. 상품 랭킹 업데이트 (비동기)
     * 2. 카트 삭제 (비동기)
     * 3. 향후 알림 발송, 통계 업데이트 등 추가 가능
     */
    @Async("taskExecutor")
    @EventListener
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info("주문 생성 이벤트 수신 - orderId: ${event.orderId}, userId: ${event.userId}")

        // 1. 상품 랭킹 업데이트 (비동기)
        updateProductRanking(event)

        // 2. 카트 삭제 (비동기)
        deleteUserCart(event)

        // 3. 향후 추가 가능한 작업들
        // - 주문 확인 알림 발송
        // - 판매 통계 업데이트
        // - 추천 시스템 업데이트
    }

    /**
     * 상품 랭킹 업데이트 (비동기)
     *
     * 주문 완료 후 상품별 주문량을 일간/주간 랭킹에 반영합니다.
     * Redis에 비동기로 업데이트되며, 실패해도 주문에는 영향을 주지 않습니다.
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

                logger.debug(
                    "상품 랭킹 업데이트 완료 - productId: {}, quantity: {}",
                    item.productId,
                    item.quantity
                )
            }

            logger.info("모든 상품 랭킹 업데이트 성공 - orderId: {}, items: {}", event.orderId, event.items.size)
        } catch (e: Exception) {
            // 랭킹 업데이트 실패해도 주문은 유효함
            logger.error("상품 랭킹 업데이트 실패 - orderId: ${event.orderId}, error: ${e.message}", e)
        }
    }

    /**
     * 카트 삭제 (비동기)
     *
     * 주문 완료 후 사용자의 카트에서 주문한 상품들을 삭제합니다.
     * 실패해도 주문에는 영향을 주지 않습니다.
     */
    private fun deleteUserCart(event: OrderCreatedEvent) {
        try {
            cartService.deleteCarts(event.userId, event.productIds)
            logger.info("카트 삭제 성공 - userId: ${event.userId}, productIds: ${event.productIds}")
        } catch (e: Exception) {
            // 카트 삭제 실패해도 주문은 유효함
            logger.error("카트 삭제 실패 - userId: ${event.userId}, error: ${e.message}", e)
        }
    }
}
