package com.hhplus.ecommerce.domains.user.dto

/**
 * 사용자 잔액 충전 API 응답 DTO
 *
 * @property userId 사용자 ID
 * @property previousBalance 충전 전 잔액 (단위: 원)
 * @property chargedAmount 충전된 금액 (단위: 원)
 * @property currentBalance 충전 후 현재 잔액 (단위: 원)
 * @property chargedAt 충전 완료 일시 (ISO 8601 형식)
 */
data class ChargeBalanceResponse(
    val userId: Long,
    val previousBalance: Long,
    val chargedAmount: Long,
    val currentBalance: Long,
    val chargedAt: String
)