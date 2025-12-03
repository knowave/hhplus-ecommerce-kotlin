package com.hhplus.ecommerce.application.payment

import com.hhplus.ecommerce.common.event.PaymentCompletedEvent

interface DataPlatformService {
    /**
     * 결제 완료 후 데이터 플랫폼으로 전송
     */
    fun sendPaymentData(event: PaymentCompletedEvent)
}
