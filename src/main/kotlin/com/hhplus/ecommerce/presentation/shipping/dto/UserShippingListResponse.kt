package com.hhplus.ecommerce.presentation.shipping.dto

import com.hhplus.ecommerce.application.shipping.dto.ShippingSummaryDto
import com.hhplus.ecommerce.application.shipping.dto.UserShippingListResult
import com.hhplus.ecommerce.application.shipping.dto.UserShippingPageInfoDto
import com.hhplus.ecommerce.domain.shipping.entity.Shipping
import java.time.LocalDateTime

/**
 * 사용자 배송 목록 조회 응답
 */
data class UserShippingListResponse(
    val userId: Long,
    val items: List<ShippingItem>,
    val page: UserShippingPageInfo,
    val summary: ShippingSummary
) {
    companion object {
        fun from(result: UserShippingListResult): UserShippingListResponse {
            return UserShippingListResponse(
                userId = result.userId,
                items = result.items.map { ShippingItem.from(it) },
                page = UserShippingPageInfo.from(result.page),
                summary = ShippingSummary.from(result.summary)
            )
        }
    }
}

/**
 * 배송 아이템
 */
data class ShippingItem(
    val shippingId: Long,
    val orderId: Long,
    val carrier: String,
    val trackingNumber: String,
    val status: String,
    val shippingStartAt: LocalDateTime?,
    val estimatedArrivalAt: LocalDateTime,
    val deliveredAt: LocalDateTime?,
    val isDelayed: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(result: Shipping): ShippingItem {
            return ShippingItem(
                shippingId = result.id,
                orderId = result.orderId,
                carrier = result.carrier,
                trackingNumber = result.trackingNumber,
                shippingStartAt = result.shippingStartAt,
                estimatedArrivalAt = result.estimatedArrivalAt,
                deliveredAt = result.deliveredAt,
                status = result.status.name,
                isDelayed = result.isDelayed,
                createdAt = result.createdAt,
                updatedAt = result.updatedAt
            )
        }
    }
}

/**
 * 페이지 정보
 */
data class UserShippingPageInfo(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
) {
    companion object {
        fun from(result: UserShippingPageInfoDto): UserShippingPageInfo {
            return UserShippingPageInfo(
                number = result.number,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages
            )
        }
    }
}

/**
 * 배송 요약
 */
data class ShippingSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val inTransitCount: Int,
    val deliveredCount: Int
) {
    companion object {
        fun from(result: ShippingSummaryDto): ShippingSummary {
            return ShippingSummary(
                totalCount = result.totalCount,
                pendingCount = result.pendingCount,
                inTransitCount = result.inTransitCount,
                deliveredCount = result.deliveredCount
            )
        }
    }
}
