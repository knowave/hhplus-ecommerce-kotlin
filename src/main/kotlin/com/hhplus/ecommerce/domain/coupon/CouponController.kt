package com.hhplus.ecommerce.domain.coupon

import com.hhplus.ecommerce.domain.coupon.dto.CouponIssueRequestDto
import com.hhplus.ecommerce.domain.coupon.dto.CouponIssueResponseDto
import com.hhplus.ecommerce.domain.coupon.dto.CouponResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService
) {

    @GetMapping
    fun getAllCoupons(): ResponseEntity<List<CouponResponseDto>> {
        val response = couponService.getAllCoupons()
        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @GetMapping("/{couponId}")
    fun getCouponById(@PathVariable couponId: String): ResponseEntity<CouponResponseDto> {
        val response = couponService.getCouponById(couponId)
        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @PostMapping("/{couponId}/issue")
    fun issueCoupon(
        @PathVariable couponId: String,
        @RequestBody dto: CouponIssueRequestDto
    ): ResponseEntity<CouponIssueResponseDto> {
        val response = couponService.issueCoupon(dto.userId, couponId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}