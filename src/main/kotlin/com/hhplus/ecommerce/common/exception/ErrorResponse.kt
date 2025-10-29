package com.hhplus.ecommerce.common.exception

data class ErrorResponse(
    val errorCode: String,
    val message: String?,
    val data: Any? = null,
    val path: String
)