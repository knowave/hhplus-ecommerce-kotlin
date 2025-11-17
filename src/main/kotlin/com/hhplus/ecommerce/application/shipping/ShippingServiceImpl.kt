package com.hhplus.ecommerce.application.shipping

import com.hhplus.ecommerce.application.shipping.dto.*
import com.hhplus.ecommerce.common.exception.AlreadyDeliveredException
import com.hhplus.ecommerce.common.exception.InvalidCarrierException
import com.hhplus.ecommerce.common.exception.InvalidEstimatedDateException
import com.hhplus.ecommerce.common.exception.InvalidStatusTransitionException
import com.hhplus.ecommerce.common.exception.OrderNotFoundForShippingException
import com.hhplus.ecommerce.common.exception.ShippingNotFoundException
import com.hhplus.ecommerce.domain.shipping.repository.ShippingJpaRepository
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import com.hhplus.ecommerce.domain.shipping.entity.ShippingStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    private val shippingRepository: ShippingJpaRepository
) : ShippingService {

    // Mock용 택배사 목록
    private val carriers = listOf("CJ대한통운", "한진택배", "롯데택배", "우체국택배")

    override fun getShipping(orderId: UUID): ShippingResult {
        val shipping = shippingRepository.findByOrderId(orderId)
            ?: throw OrderNotFoundForShippingException(orderId)

        return ShippingResult.from(shipping)
    }

    override fun updateShippingStatus(
        shippingId: UUID,
        request: UpdateShippingStatusCommand
    ): UpdateShippingStatusResult {
        // 배송 조회
        val shipping = shippingRepository.findById(shippingId)
            .orElseThrow { ShippingNotFoundException(shippingId) }

        // 이미 배송 완료인 경우 에러
        if (shipping.status == ShippingStatus.DELIVERED) {
            throw AlreadyDeliveredException(shippingId)
        }

        // 새로운 상태 파싱
        val newStatus = try {
            ShippingStatus.valueOf(request.status)
        } catch (e: IllegalArgumentException) {
            throw InvalidStatusTransitionException(shipping.status.name, request.status)
        }

        // 상태 전이 검증 (PENDING → IN_TRANSIT → DELIVERED 순서)
        validateStatusTransition(shipping.status, newStatus)

        // DELIVERED로 변경 시 deliveredAt 필수
        if (newStatus == ShippingStatus.DELIVERED && request.deliveredAt == null) {
            throw InvalidEstimatedDateException("deliveredAt is required when status is DELIVERED")
        }

        // 배송 상태 업데이트 (Entity 내부에서 isDelayed 자동 계산)
        shipping.updateStatus(newStatus, request.deliveredAt)
        shippingRepository.save(shipping)

        // 응답 생성
        return UpdateShippingStatusResult(
            shippingId = shipping.id!!,
            orderId = shipping.orderId,
            status = shipping.status.name,
            deliveredAt = shipping.deliveredAt,
            updatedAt = shipping.updatedAt!!
        )
    }

    override fun getUserShippings(
        userId: UUID,
        status: String?,
        carrier: String?,
        from: String?,
        to: String?,
        page: Int,
        size: Int
    ): UserShippingListResult {
        // 필터 파라미터 파싱
        val statusEnum = status?.let {
            try {
                ShippingStatus.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val fromDate = from?.let { parseDateTime(it) }
        val toDate = to?.let { parseDateTime(it) }

        // Pageable 생성
        val pageable = PageRequest.of(page, size)

        // 배송 목록 조회 (Order와 조인, DB 레벨 페이징 처리)
        val pagedResult = shippingRepository.findByUserIdWithFilters(
            userId = userId,
            status = statusEnum,
            carrier = carrier,
            from = fromDate,
            to = toDate,
            pageable = pageable
        )

        // Summary 계산을 위한 전체 데이터 조회
        val shippings = shippingRepository.findAllByUserIdWithFilters(
            userId = userId,
            status = statusEnum,
            carrier = carrier,
            from = fromDate,
            to = toDate
        )

        // ShippingItem으로 변환
        val items = pagedResult.content.map { shipping ->
            ShippingResult.from(shipping)
        }

        // Summary 계산 (필터링된 전체 데이터 기준)
        val summary = ShippingSummaryDto(
            totalCount = shippings.size,
            pendingCount = shippings.count { it.status == ShippingStatus.PENDING },
            inTransitCount = shippings.count { it.status == ShippingStatus.IN_TRANSIT },
            deliveredCount = shippings.count { it.status == ShippingStatus.DELIVERED }
        )

        // 페이지 정보 (Page 객체에서 직접 가져오기)
        val pageInfo = UserShippingPageInfoDto(
            number = pagedResult.number,
            size = pagedResult.size,
            totalElements = pagedResult.totalElements,
            totalPages = pagedResult.totalPages
        )

        // 응답 생성
        return UserShippingListResult(
            userId = userId,
            items = items,
            page = pageInfo,
            summary = summary
        )
    }

    override fun createShipping(orderId: UUID, carrier: String): Shipping {
        require(carriers.contains(carrier)) {
            throw InvalidCarrierException(carrier)
        }

        val now = LocalDateTime.now()
        val estimatedArrivalAt = now.plusDays(7)
        val datePart = now.toLocalDate().toString()

        val shipping = Shipping(
            orderId = orderId,
            carrier = carrier,
            trackingNumber = "TRACK${datePart}-${carrier}",
            estimatedArrivalAt = estimatedArrivalAt,
            status = ShippingStatus.PENDING,
        )

        return shippingRepository.save(shipping)
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