package com.hhplus.ecommerce.domains.user.dto

/**
 * 사용자 잔액 조회 API 응답 DTO
 *
 * @property userId 사용자 ID
 * @property balance 현재 잔액 (단위: 원)
 * @property currency 통화 단위
 * @property lastUpdatedAt 잔액 최종 업데이트 일시 (ISO 8601 형식)
 */
data class UserBalanceResponse(
    val userId: Long,
    val balance: Long,
    val currency: String,
    val lastUpdatedAt: String
)