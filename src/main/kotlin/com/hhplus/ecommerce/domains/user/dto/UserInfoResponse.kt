package com.hhplus.ecommerce.domains.user.dto

/**
 * 사용자 정보 조회 API 응답 DTO
 *
 * @property userId 사용자 ID
 * @property email 이메일 주소
 * @property name 사용자 이름
 * @property balance 현재 잔액 (단위: 원)
 * @property createdAt 계정 생성 일시 (ISO 8601 형식)
 * @property updatedAt 최종 수정 일시 (ISO 8601 형식)
 */
data class UserInfoResponse(
    val userId: Long,
    val email: String,
    val name: String,
    val balance: Long,
    val createdAt: String,
    val updatedAt: String
)