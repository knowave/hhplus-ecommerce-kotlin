package com.hhplus.ecommerce.domains.coupon

enum class CouponStatus(val description: String) {
    AVAILABLE("사용 가능"),
    USED("사용 완료"),
    EXPIRED("만료")
}