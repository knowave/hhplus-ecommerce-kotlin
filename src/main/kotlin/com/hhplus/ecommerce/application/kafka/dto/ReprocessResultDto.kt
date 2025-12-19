package com.hhplus.ecommerce.application.kafka.dto

import java.util.*

/**
 * 재처리 결과 DTO
 */
data class ReprocessResultDto(
    val id: UUID,
    val success: Boolean,
    val message: String
)