package com.hhplus.ecommerce.common.exception

class InvalidPaymentStatusException(
    paymentId: Long,
    status: String
) : RuntimeException("Invalid payment status for cancellation: paymentId=$paymentId, status=$status")