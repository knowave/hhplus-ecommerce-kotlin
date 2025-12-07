package com.hhplus.ecommerce.common.event

import com.hhplus.ecommerce.application.cart.CartService
import com.hhplus.ecommerce.application.product.ProductRankingService
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 이벤트 리스너
 *
 * 주문 관련 이벤트를 비동기로 처리합니다.
 * 트랜잭션 커밋 후에만 실행되어 데이터 정합성을 보장합니다.
 */
@Component
class OrderEventListener(
    private val cartService: CartService,
    private val productRankingService: ProductRankingService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 생성 이벤트 처리 (비동기)
     *
     * 1. 상품 랭킹 업데이트
     * 2. 카트 삭제
     */
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info("주문 생성 이벤트 수신 (Async/AfterCommit) - orderId: ${event.orderId}, userId: ${event.userId}")

        // 1. 상품 랭킹 업데이트 (비동기)
        updateProductRanking(event)

        // 2. 카트 삭제 (비동기)
        deleteUserCart(event)
    }

    /**
     * 상품 랭킹 업데이트 (비동기)
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
        }
    }

    /**
     * 카트 삭제 (비동기)
     */
    private fun deleteUserCart(event: OrderCreatedEvent) {
        try {
            cartService.deleteCarts(event.userId, event.productIds)
            logger.info("카트 삭제 성공 - userId: ${event.userId}")
        } catch (e: Exception) {
            logger.error("카트 삭제 실패 - userId: ${event.userId}, error: ${e.message}", e)
        }
    }
}
