package com.hhplus.ecommerce.application.shipping.dto

import java.util.UUID

data class UserShippingListResult(
    val userId: UUID,
    val items: List<ShippingResult>,
    val page: UserShippingPageInfoDto,
    val summary: ShippingSummaryDto
)

data class UserShippingPageInfoDto(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class ShippingSummaryDto(
    val totalCount: Int,
    val pendingCount: Int,
    val inTransitCount: Int,
    val deliveredCount: Int
)