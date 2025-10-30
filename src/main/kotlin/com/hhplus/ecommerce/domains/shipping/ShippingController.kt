package com.hhplus.ecommerce.domains.shipping

import com.hhplus.ecommerce.domains.shipping.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class ShippingController(
    private val shippingService: ShippingService
) {

    @GetMapping("/shippings/{orderId}")
    fun getShipping(@PathVariable orderId: Long): ResponseEntity<ShippingDetailResponse> {
        val response = shippingService.getShipping(orderId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/shippings/{shippingId}/status")
    fun updateShippingStatus(
        @PathVariable shippingId: Long,
        @RequestBody request: UpdateShippingStatusRequest
    ): ResponseEntity<UpdateShippingStatusResponse> {
        val response = shippingService.updateShippingStatus(shippingId, request)
        return ResponseEntity.ok(response)
    }

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
        val response = shippingService.getUserShippings(
            userId = userId,
            status = status,
            carrier = carrier,
            from = from,
            to = to,
            page = page,
            size = size
        )
        return ResponseEntity.ok(response)
    }
}
