package com.hhplus.ecommerce.domains.user.dto

/**
 * 사용자 잔액 충전 API 요청 DTO
 *
 * @property amount 충전할 금액 (단위: 원, 최소 1,000원, 최대 1,000,000원)
 */
data class ChargeBalanceRequest(
    val amount: Long
)