package com.hhplus.ecommerce.presentation.coupon

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
import com.hhplus.ecommerce.domain.coupon.CouponStatus
import com.hhplus.ecommerce.presentation.coupon.dto.*
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService
) {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 ID로 사용자에게 쿠폰을 발급합니다")
    @PostMapping("/{couponId}/issue")
    fun issueCoupon(
        @PathVariable couponId: Long,
        @RequestBody request: IssueCouponRequest
    ): ResponseEntity<IssueCouponResponse> {
        val command = IssueCouponCommand.command(request)
        val result = couponService.issueCoupon(couponId, command)

        return ResponseEntity.ok(IssueCouponResponse.from(result))
    }

    @Operation(summary = "사용 가능한 쿠폰 목록 조회", description = "현재 발급 가능한 모든 쿠폰 목록을 조회합니다")
    @GetMapping("/available")
    fun getAvailableCoupons(): ResponseEntity<AvailableCouponResponse> {
        val result = couponService.getAvailableCoupons()
        return ResponseEntity.ok(AvailableCouponResponse.from(result))
    }

    @Operation(summary = "쿠폰 상세 조회", description = "쿠폰 ID로 쿠폰의 상세 정보를 조회합니다")
    @GetMapping("/{couponId}")
    fun getCouponDetail(@PathVariable couponId: Long): ResponseEntity<CouponDetailResponse> {
        val result = couponService.getCouponDetail(couponId)
        return ResponseEntity.ok(CouponDetailResponse.from(result))
    }

    @Operation(summary = "사용자 쿠폰 목록 조회", description = "사용자 ID로 보유한 쿠폰 목록을 조회합니다. 상태별 필터링을 지원합니다")
    @GetMapping("/users/{userId}")
    fun getUserCoupons(
        @PathVariable userId: Long,
        @RequestParam(required = false) status: CouponStatus?
    ): ResponseEntity<UserCouponListResponse> {
        val result = couponService.getUserCoupons(userId, status)
        return ResponseEntity.ok(UserCouponListResponse.from(result))
    }

    @Operation(summary = "사용자의 특정 쿠폰 조회", description = "사용자 ID와 사용자 쿠폰 ID로 특정 쿠폰의 상세 정보를 조회합니다")
    @GetMapping("/users/{userId}/coupons/{userCouponId}")
    fun getUserCoupon(
        @PathVariable userId: Long,
        @PathVariable userCouponId: Long
    ): ResponseEntity<UserCouponResponse> {
        val result = couponService.getUserCoupon(userId, userCouponId)
        return ResponseEntity.ok(UserCouponResponse.from(result))
    }
}