package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.dto.CouponIssueRequestDto
import com.hhplus.ecommerce.domain.coupon.dto.CouponIssueResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService
) {

    @PostMapping("/{couponId}/issue")
    fun issueCoupon(
        @PathVariable couponId: String,
        @RequestBody dto: CouponIssueRequestDto
    ): ResponseEntity<CouponIssueResponseDto> {
        val response = couponService.issueCoupon(dto.userId, couponId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}