package com.hhplus.ecommerce.application.shipping.dto

import com.hhplus.ecommerce.domain.shipping.entity.Shipping

data class UserShippingListResult(
    val userId: Long,
    val items: List<Shipping>,
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