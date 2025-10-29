package com.hhplus.ecommerce.domains.user.dto

/**
 * Mock 사용자 데이터를 저장하기 위한 데이터 클래스
 *
 * @property userId 사용자 ID
 * @property email 이메일 주소
 * @property name 사용자 이름
 * @property balance 잔액 (단위: 원)
 * @property createdAt 생성 일시 (ISO 8601 형식)
 * @property updatedAt 최종 수정 일시 (ISO 8601 형식)
 */
data class User(
    val userId: Long,
    val email: String,
    val name: String,
    var balance: Long,
    val createdAt: String,
    var updatedAt: String
)