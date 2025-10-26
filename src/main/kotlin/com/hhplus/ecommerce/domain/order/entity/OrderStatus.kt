package com.hhplus.ecommerce.domain.order.entity

enum class OrderStatus {
    PENDING,    // 주문 생성 완료, 결제 대기
    PAID,       // 결제 완료
    CANCELLED   // 주문 취소
}
