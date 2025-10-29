package com.hhplus.ecommerce.domains.coupon

import com.hhplus.ecommerce.domains.coupon.dto.AvailableCouponResponse
import com.hhplus.ecommerce.domains.coupon.dto.CouponDetailResponse
import com.hhplus.ecommerce.domains.coupon.dto.IssueCouponRequest
import com.hhplus.ecommerce.domains.coupon.dto.IssueCouponResponse
import com.hhplus.ecommerce.domains.coupon.dto.UserCouponListResponse
import com.hhplus.ecommerce.domains.coupon.dto.UserCouponResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService
) {

    @PostMapping("/{couponId}/issue")
    fun issueCoupon(
        @PathVariable couponId: Long,
        @RequestBody request: IssueCouponRequest
    ): ResponseEntity<IssueCouponResponse> {
        val response = couponService.issueCoupon(couponId, request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/available")
    fun getAvailableCoupons(): ResponseEntity<AvailableCouponResponse> {
        val response = couponService.getAvailableCoupons()
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{couponId}")
    fun getCouponDetail(@PathVariable couponId: Long): ResponseEntity<CouponDetailResponse> {
        val response = couponService.getCouponDetail(couponId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/users/{userId}")
    fun getUserCoupons(
        @PathVariable userId: Long,
        @RequestParam(required = false) status: CouponStatus?
    ): ResponseEntity<UserCouponListResponse> {
        val response = couponService.getUserCoupons(userId, status)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/users/{userId}/coupons/{userCouponId}")
    fun getUserCoupon(
        @PathVariable userId: Long,
        @PathVariable userCouponId: Long
    ): ResponseEntity<UserCouponResponse> {
        val response = couponService.getUserCoupon(userId, userCouponId)
        return ResponseEntity.ok(response)
    }
}