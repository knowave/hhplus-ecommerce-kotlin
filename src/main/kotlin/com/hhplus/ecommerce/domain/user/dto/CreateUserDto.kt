package com.hhplus.ecommerce.domain.user.dto

import java.math.BigDecimal

data class CreateUserRequestDto(
    val balance: BigDecimal
)