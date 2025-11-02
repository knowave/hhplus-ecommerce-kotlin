package com.hhplus.ecommerce.infrastructure.shipping

import com.hhplus.ecommerce.model.shipping.Shipping
import com.hhplus.ecommerce.model.shipping.ShippingStatus
import com.hhplus.ecommerce.presentation.shipping.dto.*
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 배송 인메모리 Repository 구현체
 *
 * 이유: 실제 DB 없이 메모리에서 배송 데이터를 관리합니다.
 * Order, Cart Repository와 동일한 패턴으로 구현하여 일관성을 유지합니다.
 */
@Repository
class ShippingRepositoryImpl : ShippingRepository {

    // ID 자동 생성을 위한 카운터
    private var nextShippingId: Long = 1001L

    // Mock 데이터 저장소
    private val shippings: MutableMap<Long, Shipping> = mutableMapOf()
    private val orderIdToShippingId: MutableMap<Long, Long> = mutableMapOf()
    private val trackingNumbers: MutableSet<String> = mutableSetOf()

    // userId를 매핑하기 위한 임시 저장소 (orderId -> userId)
    // 실제로는 Order를 조회해야 하지만, Mock이므로 간단히 처리
    private val orderIdToUserId: MutableMap<Long, Long> = mutableMapOf()

    override fun findById(shippingId: Long): Shipping? {
        return shippings[shippingId]
    }

    override fun findByOrderId(orderId: Long): Shipping? {
        val shippingId = orderIdToShippingId[orderId] ?: return null
        return shippings[shippingId]
    }

    override fun findByUserId(userId: Long): List<Shipping> {
        val userOrderIds = orderIdToUserId.entries
            .filter { it.value == userId }
            .map { it.key }
            .toSet()

        return shippings.values
            .filter { it.orderId in userOrderIds }
            .sortedByDescending { it.createdAt }
    }

    override fun findByUserIdWithFilters(
        userId: Long,
        status: ShippingStatus?,
        carrier: String?,
        from: LocalDateTime?,
        to: LocalDateTime?
    ): List<Shipping> {
        val userOrderIds = orderIdToUserId.entries
            .filter { it.value == userId }
            .map { it.key }
            .toSet()

        return shippings.values
            .filter { shipping ->
                var matches = shipping.orderId in userOrderIds

                if (status != null) {
                    matches = matches && shipping.status == status
                }

                if (carrier != null) {
                    matches = matches && shipping.carrier == carrier
                }

                if (from != null) {
                    matches = matches && shipping.createdAt >= from
                }

                if (to != null) {
                    matches = matches && shipping.createdAt <= to
                }

                matches
            }
            .sortedByDescending { it.createdAt }
    }

    override fun existsByTrackingNumber(trackingNumber: String): Boolean {
        return trackingNumber in trackingNumbers
    }

    override fun save(shipping: Shipping): Shipping {
        shippings[shipping.id] = shipping
        orderIdToShippingId[shipping.orderId] = shipping.id
        trackingNumbers.add(shipping.trackingNumber)
        return shipping
    }

    override fun generateId(): Long {
        return nextShippingId++
    }

    override fun generateTrackingNumber(): String {
        var trackingNumber: String
        do {
            // 12자리 랜덤 숫자 생성
            trackingNumber = (1..12).map { Random.nextInt(0, 10) }.joinToString("")
        } while (existsByTrackingNumber(trackingNumber))
        return trackingNumber
    }

    /**
     * Mock 용: orderId와 userId 매핑 저장
     * 실제 구현에서는 Order를 조회하여 userId를 가져옴
     */
    fun associateOrderWithUser(orderId: Long, userId: Long) {
        orderIdToUserId[orderId] = userId
    }
}
