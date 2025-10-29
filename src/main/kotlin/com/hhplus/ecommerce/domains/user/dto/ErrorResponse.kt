package com.hhplus.ecommerce.domains.user.dto

/**
 * API 에러 응답 DTO
 *
 * @property timestamp 에러 발생 일시 (ISO 8601 형식)
 * @property status HTTP 상태 코드
 * @property error HTTP 상태 메시지
 * @property code 에러 코드 (예: USER_NOT_FOUND, INVALID_AMOUNT 등)
 * @property message 사용자에게 표시할 에러 메시지
 * @property path 에러가 발생한 API 경로
 */
data class ErrorResponse(
    val timestamp: String,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String
)