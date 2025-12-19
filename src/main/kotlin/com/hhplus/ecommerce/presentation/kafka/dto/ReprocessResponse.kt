package com.hhplus.ecommerce.presentation.kafka.dto

import com.hhplus.ecommerce.application.kafka.dto.ReprocessResultDto
import java.util.*

/**
 * 재처리 결과 응답 DTO
 */
data class ReprocessResponse(
    val id: UUID,
    val success: Boolean,
    val message: String
) {
    companion object {
        fun from(dto: ReprocessResultDto): ReprocessResponse {
            return ReprocessResponse(
                id = dto.id,
                success = dto.success,
                message = dto.message
            )
        }
    }
}