package com.hhplus.ecommerce.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val code: String,
    val message: String,
    val status: HttpStatus
) {
    // Product related
    PRODUCT_NOT_FOUND("P001", "Product not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("P002", "Insufficient stock", HttpStatus.BAD_REQUEST),

    // Cart related
    CART_ITEM_NOT_FOUND("CART001", "Cart item not found", HttpStatus.NOT_FOUND),
    EXCEED_MAX_QUANTITY("CART002", "Exceed max quantity", HttpStatus.BAD_REQUEST),

    // Order related
    INVALID_QUANTITY("O001", "Invalid quantity", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND("O002", "Order not found", HttpStatus.NOT_FOUND),
    ORDER_ALREADY_PAID("O003", "Order already paid", HttpStatus.BAD_REQUEST),

    // Payment related
    INSUFFICIENT_BALANCE("PAY001", "Insufficient balance", HttpStatus.BAD_REQUEST),
    PAYMENT_FAILED("PAY002", "Payment failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // Coupon related
    COUPON_SOLD_OUT("C001", "Coupon sold out", HttpStatus.BAD_REQUEST),
    INVALID_COUPON("C002", "Invalid coupon", HttpStatus.BAD_REQUEST),
    EXPIRED_COUPON("C003", "Expired coupon", HttpStatus.BAD_REQUEST),
    ALREADY_USED_COUPON("C004", "Coupon already used", HttpStatus.BAD_REQUEST),
    COUPON_NOT_FOUND("C005", "Coupon not found", HttpStatus.NOT_FOUND),
    COUPON_ALREADY_ISSUED("C006", "Coupon already issued to user", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_RATE("C007", "Invalid discount rate", HttpStatus.BAD_REQUEST),
    INVALID_COUPON_QUANTITY("C008", "Invalid coupon quantity", HttpStatus.BAD_REQUEST),
    INVALID_COUPON_DATE("C009", "Invalid coupon date", HttpStatus.BAD_REQUEST),

    // User Coupon related
    USER_COUPON_NOT_FOUND("UC001", "User coupon not found", HttpStatus.NOT_FOUND),

    // User related
    USER_NOT_FOUND("U001", "User not found", HttpStatus.NOT_FOUND),
    DUPLICATE_EMAIL("U002", "Email already exists", HttpStatus.CONFLICT),
    INVALID_PASSWORD("U003", "Invalid password", HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT("U004", "Invalid amount", HttpStatus.BAD_REQUEST),
    BALANCE_LIMIT_EXCEEDED("U005", "Balance limit exceeded", HttpStatus.BAD_REQUEST),

    // Authentication related
    UNAUTHORIZED("AUTH001", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH002", "Access denied", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("AUTH003", "Invalid token", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("AUTH004", "Token expired", HttpStatus.UNAUTHORIZED),

    // Common
    INVALID_INPUT("COMMON001", "Invalid input value", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("COMMON002", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
}