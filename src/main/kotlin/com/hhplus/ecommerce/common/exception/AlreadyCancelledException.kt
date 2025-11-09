package com.hhplus.ecommerce.common.exception

class AlreadyCancelledException(
    paymentId: Long
) : RuntimeException("Payment already cancelled: paymentId=$paymentId")