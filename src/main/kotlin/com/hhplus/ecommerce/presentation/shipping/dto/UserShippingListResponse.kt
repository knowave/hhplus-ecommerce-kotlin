package com.hhplus.ecommerce.presentation.shipping.dto

import java.time.LocalDateTime

/**
 * 사용자 배송 목록 조회 응답
 */
data class UserShippingListResponse(
    val userId: Long,
    val items: List<ShippingItem>,
    val page: PageInfo,
    val summary: ShippingSummary
)

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
)

/**
 * 페이지 정보
 */
data class PageInfo(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

/**
 * 배송 요약
 */
data class ShippingSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val inTransitCount: Int,
    val deliveredCount: Int
)
