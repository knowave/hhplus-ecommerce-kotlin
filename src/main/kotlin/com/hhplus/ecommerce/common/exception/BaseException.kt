package com.hhplus.ecommerce.common.exception

import com.hhplus.ecommerce.domain.order.entity.OrderStatus
import java.math.BigDecimal
import java.util.*

open class BaseException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    val data: Any? = null
) : RuntimeException(message)

// Product related
class ProductNotFoundException(productId: UUID) : BaseException(
    errorCode = ErrorCode.PRODUCT_NOT_FOUND,
    message = "Product not found with id: $productId"
)

class InsufficientStockException(
    productId: UUID,
    requested: Int,
    available: Int
) : BaseException(
    errorCode = ErrorCode.INSUFFICIENT_STOCK,
    message = "Insufficient stock. Requested: $requested, Available: $available",
    data = mapOf(
        "productId" to productId,
        "requested" to requested,
        "available" to available
    )
)

// Cart related
class CartItemNotFoundException(cartItemId: UUID) : BaseException(
    errorCode = ErrorCode.CART_ITEM_NOT_FOUND,
    message = "Cart item not found with id: $cartItemId"
)

class ExceedMaxQuantityException(
    maxQuantity: Int,
    attempted: Int
) : BaseException(
    errorCode = ErrorCode.EXCEED_MAX_QUANTITY,
    message = "Exceed max quantity. Max: $maxQuantity, Attempted: $attempted",
    data = mapOf(
        "maxQuantity" to maxQuantity,
        "attempted" to attempted
    )
)

class InvalidCartItemException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_CART,
    message = message
)

class ForbiddenException(message: String) : BaseException(
    errorCode = ErrorCode.FORBIDDEN,
    message = message
)

// Order related
class OrderNotFoundException(orderId: UUID) : BaseException(
    errorCode = ErrorCode.ORDER_NOT_FOUND,
    message = "Order not found with id: $orderId"
)

class InvalidQuantityException(quantity: Int) : BaseException(
    errorCode = ErrorCode.INVALID_QUANTITY,
    message = "Invalid quantity: $quantity"
)

class OrderAlreadyPaidException(orderId: UUID) : BaseException(
    errorCode = ErrorCode.ORDER_ALREADY_PAID,
    message = "Order already paid. Order id: $orderId"
)

class InvalidOrderItemsException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_ORDER_ITEMS,
    message = message
)

class OrderAlreadyCancelledException(status: OrderStatus) : BaseException(
    errorCode = ErrorCode.ORDER_ALREADY_CANCELLED,
    message = "Order is already cancelled. status: $status"
)

class OrderNotRefundableException(status: OrderStatus) : BaseException(
    errorCode = ErrorCode.ORDER_NOT_REFUNDABLE,
    message = "Order cannot be refunded. Current status: $status"
)

// Payment related
class InsufficientBalanceException(
    required: Long,
    available: Long
) : BaseException(
    errorCode = ErrorCode.INSUFFICIENT_BALANCE,
    message = "Insufficient balance. Required: $required, Available: $available",
    data = mapOf(
        "required" to required,
        "available" to available
    )
)

class PaymentFailedException(reason: String) : BaseException(
    errorCode = ErrorCode.PAYMENT_FAILED,
    message = "Payment failed. Reason: $reason"
)

class PaymentNotFoundException(paymentId: UUID) : BaseException(
    errorCode = ErrorCode.PAYMENT_NOT_FOUND,
    message = "Payment not found with id: $paymentId"
)

class InvalidOrderStatusException(orderId: UUID, currentStatus: String) : BaseException(
    errorCode = ErrorCode.INVALID_ORDER_STATUS,
    message = "Invalid order status for payment. Order id: $orderId, Status: $currentStatus"
)

class AlreadyPaidException(orderId: UUID) : BaseException(
    errorCode = ErrorCode.ALREADY_PAID,
    message = "Order already paid. Order id: $orderId"
)

class AlreadyCancelledException(
    paymentId: UUID
) : RuntimeException("Payment already cancelled: paymentId=$paymentId")

class InvalidPaymentStatusException(
    paymentId: UUID,
    status: String
) : RuntimeException("Invalid payment status for cancellation: paymentId=$paymentId, status=$status")

// Data Transmission related
class TransmissionNotFoundException(transmissionId: UUID) : BaseException(
    errorCode = ErrorCode.TRANSMISSION_NOT_FOUND,
    message = "Data transmission not found with id: $transmissionId"
)

class AlreadySuccessException(transmissionId: UUID) : BaseException(
    errorCode = ErrorCode.ALREADY_SUCCESS,
    message = "Transmission already successful. Transmission id: $transmissionId"
)

// Coupon related
class CouponSoldOutException(couponId: UUID) : BaseException(
    errorCode = ErrorCode.COUPON_SOLD_OUT,
    message = "Coupon sold out. Coupon id: $couponId"
)

class InvalidCouponException(reason: String) : BaseException(
    errorCode = ErrorCode.INVALID_COUPON,
    message = "Invalid coupon. Reason: $reason"
)

class ExpiredCouponException(couponId: UUID) : BaseException(
    errorCode = ErrorCode.EXPIRED_COUPON,
    message = "Coupon expired. Coupon id: $couponId"
)

class AlreadyUsedCouponException(couponId: UUID) : BaseException(
    errorCode = ErrorCode.ALREADY_USED_COUPON,
    message = "Coupon already used. Coupon id: $couponId"
)

class CouponNotFoundException(couponId: UUID) : BaseException(
    errorCode = ErrorCode.COUPON_NOT_FOUND,
    message = "Coupon not found with id: $couponId"
)

class CouponAlreadyIssuedException(userId: UUID, couponId: UUID) : BaseException(
    errorCode = ErrorCode.COUPON_ALREADY_ISSUED,
    message = "User already has this coupon. User id: $userId, Coupon id: $couponId"
)

class UserCouponNotFoundException(userId: UUID, userCouponId: UUID) : BaseException(
    errorCode = ErrorCode.USER_COUPON_NOT_FOUND,
    message = "User Coupon Not found userId: $userId, userCouponId: $userCouponId"
)

class InvalidDiscountRateException(discountRate: Int) : BaseException(
    errorCode = ErrorCode.INVALID_DISCOUNT_RATE,
    message = "Discount rate must be between 1 and 100. Given: $discountRate"
)

class InvalidCouponQuantityException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_COUPON_QUANTITY,
    message = message
)

class InvalidCouponDateException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_COUPON_DATE,
    message = message
)

// User related
class UserNotFoundException(userId: UUID) : BaseException(
    errorCode = ErrorCode.USER_NOT_FOUND,
    message = "User not found with id: $userId"
)

class DuplicateEmailException(email: String) : BaseException(
    errorCode = ErrorCode.DUPLICATE_EMAIL,
    message = "Email already exists: $email"
)

class InvalidPasswordException : BaseException(
    errorCode = ErrorCode.INVALID_PASSWORD,
    message = "Invalid password"
)

class InvalidAmountException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_AMOUNT,
    message = message
)

class BalanceLimitExceededException(
    limit: Long,
    attempted: Long
) : BaseException(
    errorCode = ErrorCode.BALANCE_LIMIT_EXCEEDED,
    message = "Balance limit exceeded. Limit: $limit, Attempted: $attempted",
    data = mapOf(
        "limit" to limit,
        "attempted" to attempted
    )
)

// Shipping related
class ShippingNotFoundException(shippingId: UUID) : BaseException(
    errorCode = ErrorCode.SHIPPING_NOT_FOUND,
    message = "Shipping not found with id: $shippingId"
)

class OrderNotFoundForShippingException(orderId: UUID) : BaseException(
    errorCode = ErrorCode.ORDER_NOT_FOUND_FOR_SHIPPING,
    message = "Order not found with id: $orderId"
)

class InvalidEstimatedDateException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_ESTIMATED_DATE,
    message = message
)

class InvalidCarrierException(carrier: String) : BaseException(
    errorCode = ErrorCode.INVALID_ESTIMATED_DATE,
    message = "carrier is invalid value"
)

class DuplicateTrackingNumberException(trackingNumber: String) : BaseException(
    errorCode = ErrorCode.DUPLICATE_TRACKING_NUMBER,
    message = "Tracking number already exists: $trackingNumber"
)

class ShippingAlreadyExistsException(orderId: UUID) : BaseException(
    errorCode = ErrorCode.SHIPPING_ALREADY_EXISTS,
    message = "Shipping already exists for order id: $orderId"
)

class InvalidStatusTransitionException(currentStatus: String, newStatus: String) : BaseException(
    errorCode = ErrorCode.INVALID_STATUS_TRANSITION,
    message = "Cannot transition from $currentStatus to $newStatus"
)

class AlreadyDeliveredException(shippingId: UUID) : BaseException(
    errorCode = ErrorCode.ALREADY_DELIVERED,
    message = "Shipping already delivered. Shipping id: $shippingId"
)