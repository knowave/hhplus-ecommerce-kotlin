package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.application.shipping.dto.*
import com.hhplus.ecommerce.common.exception.AlreadyDeliveredException
import com.hhplus.ecommerce.common.exception.InvalidEstimatedDateException
import com.hhplus.ecommerce.common.exception.InvalidStatusTransitionException
import com.hhplus.ecommerce.common.exception.OrderNotFoundForShippingException
import com.hhplus.ecommerce.common.exception.ShippingNotFoundException
import com.hhplus.ecommerce.domain.shipping.ShippingRepository
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import com.hhplus.ecommerce.presentation.shipping.dto.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 배송 비즈니스 로직 구현체
 *
 * Mock Server 구현 목적:
 * - 실제 DB와 외부 배송 시스템 없이 배송 관리 기능 시뮬레이션
 * - API 문서에 정의된 비즈니스 규칙 구현
 * - 배송 상태 전이 규칙 및 검증 로직 구현
 */
@Service
class ShippingServiceImpl(
    private val shippingRepository: ShippingRepository
) : ShippingService {

    // Mock용 택배사 목록
    private val carriers = listOf("CJ대한통운", "한진택배", "롯데택배", "우체국택배")

    override fun getShipping(orderId: Long): Shipping {
        // 1. 주문 ID로 배송 조회
        val shipping = shippingRepository.findByOrderId(orderId)
            ?: throw OrderNotFoundForShippingException(orderId)

        // 2. 응답 생성
        return Shipping(
            id = shipping.id,
            orderId = shipping.orderId,
            carrier = shipping.carrier,
            trackingNumber = shipping.trackingNumber,
            shippingStartAt = shipping.shippingStartAt,
            estimatedArrivalAt = shipping.estimatedArrivalAt,
            deliveredAt = shipping.deliveredAt,
            status = shipping.status,
            isDelayed = shipping.isDelayed,
            isExpired = shipping.isExpired,
            createdAt = shipping.createdAt,
            updatedAt = shipping.updatedAt
        )
    }

    override fun updateShippingStatus(
        shippingId: Long,
        request: UpdateShippingStatusCommand
    ): UpdateShippingStatusResult {
        // 1. 배송 조회
        val shipping = shippingRepository.findById(shippingId)
            ?: throw ShippingNotFoundException(shippingId)

        // 2. 이미 배송 완료인 경우 에러
        if (shipping.status == ShippingStatus.DELIVERED) {
            throw AlreadyDeliveredException(shippingId)
        }

        // 3. 새로운 상태 파싱
        val newStatus = try {
            ShippingStatus.valueOf(request.status)
        } catch (e: IllegalArgumentException) {
            throw InvalidStatusTransitionException(shipping.status.name, request.status)
        }

        // 4. 상태 전이 검증 (PENDING → IN_TRANSIT → DELIVERED 순서)
        validateStatusTransition(shipping.status, newStatus)

        // 5. DELIVERED로 변경 시 deliveredAt 필수
        if (newStatus == ShippingStatus.DELIVERED && request.deliveredAt == null) {
            throw InvalidEstimatedDateException("deliveredAt is required when status is DELIVERED")
        }

        // 6. 지연 여부 계산 (DELIVERED 상태일 때만)
        val isDelayed = if (newStatus == ShippingStatus.DELIVERED && request.deliveredAt != null) {
            request.deliveredAt.isAfter(shipping.estimatedArrivalAt)
        } else {
            shipping.isDelayed
        }

        // 7. 배송 정보 업데이트
        val updatedShipping = shipping.copy(
            status = newStatus,
            deliveredAt = request.deliveredAt,
            isDelayed = isDelayed,
            updatedAt = LocalDateTime.now()
        )
        shippingRepository.save(updatedShipping)

        // 8. 응답 생성
        return UpdateShippingStatusResult(
            shippingId = updatedShipping.id,
            orderId = updatedShipping.orderId,
            status = updatedShipping.status.name,
            deliveredAt = updatedShipping.deliveredAt,
            updatedAt = updatedShipping.updatedAt
        )
    }

    override fun getUserShippings(
        userId: Long,
        status: String?,
        carrier: String?,
        from: String?,
        to: String?,
        page: Int,
        size: Int
    ): UserShippingListResult {
        // 1. 필터 파라미터 파싱
        val statusEnum = status?.let {
            try {
                ShippingStatus.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val fromDate = from?.let { parseDateTime(it) }
        val toDate = to?.let { parseDateTime(it) }

        // 2. 배송 목록 조회 (필터링 적용)
        val allShippings = shippingRepository.findByUserIdWithFilters(
            userId = userId,
            status = statusEnum,
            carrier = carrier,
            from = fromDate,
            to = toDate
        )

        // 3. 페이징 처리
        val totalElements = allShippings.size.toLong()
        val totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 1
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, allShippings.size)
        val pagedShippings = if (startIndex < allShippings.size) {
            allShippings.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // 4. ShippingItem으로 변환
        val items = pagedShippings.map { shipping ->
            Shipping(
                id = shipping.id,
                orderId = shipping.orderId,
                carrier = shipping.carrier,
                trackingNumber = shipping.trackingNumber,
                status = shipping.status,
                shippingStartAt = shipping.shippingStartAt,
                estimatedArrivalAt = shipping.estimatedArrivalAt,
                deliveredAt = shipping.deliveredAt,
                isDelayed = shipping.isDelayed,
                createdAt = shipping.createdAt,
                updatedAt = shipping.updatedAt
            )
        }

        // 5. Summary 계산 (전체 데이터 기준)
        val summary = ShippingSummaryDto(
            totalCount = allShippings.size,
            pendingCount = allShippings.count { it.status == ShippingStatus.PENDING },
            inTransitCount = allShippings.count { it.status == ShippingStatus.IN_TRANSIT },
            deliveredCount = allShippings.count { it.status == ShippingStatus.DELIVERED }
        )

        // 6. 페이지 정보
        val pageInfo = UserShippingPageInfoDto(
            number = page,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages
        )

        // 7. 응답 생성
        return UserShippingListResult(
            userId = userId,
            items = items,
            page = pageInfo,
            summary = summary
        )
    }

    /**
     * 상태 전이 유효성 검증
     * PENDING → IN_TRANSIT → DELIVERED 순서만 허용
     */
    private fun validateStatusTransition(current: ShippingStatus, new: ShippingStatus) {
        val validTransitions = mapOf(
            ShippingStatus.PENDING to listOf(ShippingStatus.IN_TRANSIT),
            ShippingStatus.IN_TRANSIT to listOf(ShippingStatus.DELIVERED),
            ShippingStatus.DELIVERED to emptyList()
        )

        val allowedStatuses = validTransitions[current] ?: emptyList()
        if (new !in allowedStatuses && current != new) {
            throw InvalidStatusTransitionException(current.name, new.name)
        }
    }

    /**
     * ISO 8601 형식의 문자열을 LocalDateTime으로 변환
     */
    private fun parseDateTime(dateTimeStr: String): LocalDateTime {
        return try {
            LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            throw InvalidEstimatedDateException("Invalid date format: $dateTimeStr. Expected ISO 8601 format.")
        }
    }
}