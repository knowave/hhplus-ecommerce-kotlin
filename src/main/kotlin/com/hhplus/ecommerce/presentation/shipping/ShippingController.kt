package com.hhplus.ecommerce.presentation.shipping

import com.hhplus.ecommerce.application.shipping.ShippingService
import com.hhplus.ecommerce.application.shipping.dto.UpdateShippingStatusCommand
import com.hhplus.ecommerce.presentation.shipping.dto.*
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/shippings")
class ShippingController(
    private val shippingService: ShippingService
) {

    @Operation(summary = "배송 정보 조회", description = "주문 ID로 배송 정보를 조회합니다")
    @GetMapping("{orderId}")
    fun getShipping(@PathVariable orderId: Long): ResponseEntity<ShippingDetailResponse> {
        val result = shippingService.getShipping(orderId)
        return ResponseEntity.ok(ShippingDetailResponse.from(result))
    }

    @Operation(summary = "배송 상태 수정", description = "배송 ID로 배송 상태를 수정합니다")
    @PatchMapping("{shippingId}/status")
    fun updateShippingStatus(
        @PathVariable shippingId: Long,
        @RequestBody request: UpdateShippingStatusRequest
    ): ResponseEntity<UpdateShippingStatusResponse> {
        val command = UpdateShippingStatusCommand.command(request)
        val result = shippingService.updateShippingStatus(shippingId, command)

        return ResponseEntity.ok(UpdateShippingStatusResponse.from(result))
    }

    @Operation(summary = "사용자 배송 목록 조회", description = "사용자 ID로 배송 목록을 조회합니다. 상태, 배송사, 기간 필터링 및 페이징을 지원합니다")
    @GetMapping("/users/{userId}/shippings")
    fun getUserShippings(
        @PathVariable userId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) carrier: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<UserShippingListResponse> {
        val result = shippingService.getUserShippings(
            userId = userId,
            status = status,
            carrier = carrier,
            from = from,
            to = to,
            page = page,
            size = size
        )
        return ResponseEntity.ok(UserShippingListResponse.from(result))
    }
}
