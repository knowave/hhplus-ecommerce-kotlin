package com.hhplus.ecommerce.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalException {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(
        ex: BaseException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("exception occurred: ${ex.errorCode.code} - ${ex.message}")

        val errorResponse = ErrorResponse(
            errorCode = ex.errorCode.code,
            message = ex.message,
            data = ex.data,
            path = request.requestURI
        )

        return ResponseEntity
            .status(ex.errorCode.status)
            .body(errorResponse)
    }
}