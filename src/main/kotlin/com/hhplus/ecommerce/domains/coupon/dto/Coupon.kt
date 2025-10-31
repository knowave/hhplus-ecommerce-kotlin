package com.hhplus.ecommerce.domains.coupon.dto

data class Coupon(
    val id: Long,
    val name: String,
    val description: String,
    val discountRate: Int,
    val totalQuantity: Int,
    var issuedQuantity: Int,
    val startDate: String,
    val endDate: String,
    val validityDays: Int,
    val createdAt: String
)