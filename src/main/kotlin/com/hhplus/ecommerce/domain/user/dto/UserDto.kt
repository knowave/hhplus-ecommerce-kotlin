package com.hhplus.ecommerce.domain.user.dto

import java.math.BigDecimal

data class UserResponseDto(
    val id: String,
    val balance: BigDecimal
)