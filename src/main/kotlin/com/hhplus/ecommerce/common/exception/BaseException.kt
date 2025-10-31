package com.hhplus.ecommerce.common.exception

import java.math.BigDecimal

open class BaseException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    val data: Any? = null
) : RuntimeException(message)

// Product related
class ProductNotFoundException(productId: Long) : BaseException(
    errorCode = ErrorCode.PRODUCT_NOT_FOUND,
    message = "Product not found with id: $productId"
)

class InsufficientStockException(
    productId: Long,
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
class CartItemNotFoundException(cartItemId: Long) : BaseException(
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

class ForbiddenException(message: String) : BaseException(
    errorCode = ErrorCode.FORBIDDEN,
    message = message
)

// Order related
class OrderNotFoundException(orderId: Long) : BaseException(
    errorCode = ErrorCode.ORDER_NOT_FOUND,
    message = "Order not found with id: $orderId"
)

class InvalidQuantityException(quantity: Int) : BaseException(
    errorCode = ErrorCode.INVALID_QUANTITY,
    message = "Invalid quantity: $quantity"
)

class OrderAlreadyPaidException(orderId: Long) : BaseException(
    errorCode = ErrorCode.ORDER_ALREADY_PAID,
    message = "Order already paid. Order id: $orderId"
)

class InvalidOrderItemsException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_ORDER_ITEMS,
    message = message
)

class CannotCancelOrderException(orderId: Long, currentStatus: String) : BaseException(
    errorCode = ErrorCode.CANNOT_CANCEL_ORDER,
    message = "Cannot cancel order. Order id: $orderId, Current status: $currentStatus"
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

class PaymentNotFoundException(paymentId: Long) : BaseException(
    errorCode = ErrorCode.PAYMENT_NOT_FOUND,
    message = "Payment not found with id: $paymentId"
)

class InvalidOrderStatusException(orderId: Long, currentStatus: String) : BaseException(
    errorCode = ErrorCode.INVALID_ORDER_STATUS,
    message = "Invalid order status for payment. Order id: $orderId, Status: $currentStatus"
)

class AlreadyPaidException(orderId: Long) : BaseException(
    errorCode = ErrorCode.ALREADY_PAID,
    message = "Order already paid. Order id: $orderId"
)

// Data Transmission related
class TransmissionNotFoundException(transmissionId: Long) : BaseException(
    errorCode = ErrorCode.TRANSMISSION_NOT_FOUND,
    message = "Data transmission not found with id: $transmissionId"
)

class AlreadySuccessException(transmissionId: Long) : BaseException(
    errorCode = ErrorCode.ALREADY_SUCCESS,
    message = "Transmission already successful. Transmission id: $transmissionId"
)

// Coupon related
class CouponSoldOutException(couponId: Long) : BaseException(
    errorCode = ErrorCode.COUPON_SOLD_OUT,
    message = "Coupon sold out. Coupon id: $couponId"
)

class InvalidCouponException(reason: String) : BaseException(
    errorCode = ErrorCode.INVALID_COUPON,
    message = "Invalid coupon. Reason: $reason"
)

class ExpiredCouponException(couponId: Long) : BaseException(
    errorCode = ErrorCode.EXPIRED_COUPON,
    message = "Coupon expired. Coupon id: $couponId"
)

class AlreadyUsedCouponException(couponId: Long) : BaseException(
    errorCode = ErrorCode.ALREADY_USED_COUPON,
    message = "Coupon already used. Coupon id: $couponId"
)

class CouponNotFoundException(couponId: Long) : BaseException(
    errorCode = ErrorCode.COUPON_NOT_FOUND,
    message = "Coupon not found with id: $couponId"
)

class CouponAlreadyIssuedException(userId: Long, couponId: Long) : BaseException(
    errorCode = ErrorCode.COUPON_ALREADY_ISSUED,
    message = "User already has this coupon. User id: $userId, Coupon id: $couponId"
)

class UserCouponNotFoundException(userId: Long, userCouponId: Long) : BaseException(
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
class UserNotFoundException(userId: Long) : BaseException(
    errorCode = ErrorCode.USER_NOT_FOUND,
    message = "User not found with id: $userId"
)

class DuplicateEmailException(email: Long) : BaseException(
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
class ShippingNotFoundException(shippingId: Long) : BaseException(
    errorCode = ErrorCode.SHIPPING_NOT_FOUND,
    message = "Shipping not found with id: $shippingId"
)

class OrderNotFoundForShippingException(orderId: Long) : BaseException(
    errorCode = ErrorCode.ORDER_NOT_FOUND_FOR_SHIPPING,
    message = "Order not found with id: $orderId"
)

class InvalidEstimatedDateException(message: String) : BaseException(
    errorCode = ErrorCode.INVALID_ESTIMATED_DATE,
    message = message
)

class DuplicateTrackingNumberException(trackingNumber: String) : BaseException(
    errorCode = ErrorCode.DUPLICATE_TRACKING_NUMBER,
    message = "Tracking number already exists: $trackingNumber"
)

class ShippingAlreadyExistsException(orderId: Long) : BaseException(
    errorCode = ErrorCode.SHIPPING_ALREADY_EXISTS,
    message = "Shipping already exists for order id: $orderId"
)

class InvalidStatusTransitionException(currentStatus: String, newStatus: String) : BaseException(
    errorCode = ErrorCode.INVALID_STATUS_TRANSITION,
    message = "Cannot transition from $currentStatus to $newStatus"
)

class AlreadyDeliveredException(shippingId: Long) : BaseException(
    errorCode = ErrorCode.ALREADY_DELIVERED,
    message = "Shipping already delivered. Shipping id: $shippingId"
)