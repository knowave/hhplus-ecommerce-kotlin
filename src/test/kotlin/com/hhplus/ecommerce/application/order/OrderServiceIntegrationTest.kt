package com.hhplus.ecommerce.application.order

import com.hhplus.ecommerce.application.coupon.CouponService
import com.hhplus.ecommerce.application.product.ProductService
import com.hhplus.ecommerce.application.user.UserService
import com.hhplus.ecommerce.application.user.dto.CreateUserCommand
import com.hhplus.ecommerce.common.exception.InsufficientStockException
import com.hhplus.ecommerce.domain.coupon.entity.Coupon
import com.hhplus.ecommerce.domain.coupon.repository.CouponJpaRepository
import com.hhplus.ecommerce.domain.coupon.repository.CouponStatus
import com.hhplus.ecommerce.domain.coupon.repository.UserCouponJpaRepository
import com.hhplus.ecommerce.domain.order.repository.OrderJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.application.order.dto.*
import com.hhplus.ecommerce.application.coupon.dto.IssueCouponCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
class OrderServiceIntegrationTest(
    private val orderRepository: OrderJpaRepository,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val userService: UserService,
    private val orderService: OrderService,
    private val couponRepository: CouponJpaRepository,
    private val userCouponRepository: UserCouponJpaRepository
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var testUserId: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID
    private lateinit var couponId: UUID

    init {
        beforeEach {
            // 사용자 생성
            val createUserCommand = CreateUserCommand(balance = 500000L)
            val savedUser = userService.createUser(createUserCommand)
            testUserId = savedUser.id!!

            // 상품 생성
            val product1 = Product(
                name = "노트북",
                description = "고성능 노트북",
                price = 100000L,
                stock = 10,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 0
            )
            val savedProduct1 = productService.updateProduct(product1)
            product1Id = savedProduct1.id!!

            val product2 = Product(
                name = "마우스",
                description = "무선 마우스",
                price = 30000L,
                stock = 20,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 0
            )
            val savedProduct2 = productService.updateProduct(product2)
            product2Id = savedProduct2.id!!

            // 쿠폰 생성
            val coupon = Coupon(
                name = "10% 할인 쿠폰",
                description = "전품목 10% 할인",
                discountRate = 10,
                totalQuantity = 100,
                issuedQuantity = 0,
                startDate = LocalDateTime.now(),
                endDate = LocalDateTime.now().plusDays(30),
                validityDays = 30
            )
            val savedCoupon = couponRepository.save(coupon)
            couponId = savedCoupon.id!!
        }

        afterEach {
            // 테스트 데이터 정리
            userCouponRepository.deleteAll()
            orderRepository.deleteAll()
        }

        describe("OrderService 통합 테스트 - 주문 전체 플로우") {

            context("주문 생성 및 조회 통합 시나리오") {
                it("사용자가 상품을 주문하고 조회할 수 있다") {
                    // given
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 2)),
                        couponId = null
                    )

                    // when - 주문 생성
                    val orderResponse = orderService.createOrder(command)

                    // then - 주문 생성 검증
                    orderResponse.orderId shouldNotBe null
                    orderResponse.userId shouldBe testUserId
                    orderResponse.items.size shouldBe 1
                    orderResponse.pricing.totalAmount shouldBe 200000L
                    orderResponse.pricing.finalAmount shouldBe 200000L
                    orderResponse.status shouldBe "PENDING"

                    // when - 주문 조회
                    val orderDetail = orderService.getOrderDetail(orderResponse.orderId, testUserId)

                    // then - 주문 상세 정보 검증
                    orderDetail.orderId shouldBe orderResponse.orderId
                    orderDetail.userId shouldBe testUserId
                    orderDetail.status shouldBe "PENDING"
                    orderDetail.pricing.totalAmount shouldBe 200000L

                    // then - 재고 차감 검증
                    val updatedProduct = productService.findProductById(product1Id)
                    updatedProduct.stock shouldBe 8 // 10 - 2 = 8
                }

                it("쿠폰을 사용하여 주문할 수 있다") {
                    // given - 쿠폰 발급
                    val issueCouponCommand = IssueCouponCommand(userId = testUserId)
                    couponService.issueCoupon(couponId, issueCouponCommand)

                    // given - 주문 생성
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 1)),
                        couponId = couponId
                    )

                    // when
                    val orderResponse = orderService.createOrder(command)

                    // then
                    orderResponse.pricing.totalAmount shouldBe 100000L
                    orderResponse.pricing.discountAmount shouldBe 10000L // 10% 할인
                    orderResponse.pricing.finalAmount shouldBe 90000L
                    orderResponse.pricing.appliedCoupon shouldNotBe null
                    orderResponse.pricing.appliedCoupon?.couponId shouldBe couponId

                    // then - 쿠폰 사용 처리 검증
                    val userCoupon = couponService.getUserCoupons(testUserId, CouponStatus.USED)
                    userCoupon.coupons.size shouldBe 1
                    userCoupon.coupons[0].status shouldBe "USED"
                }

                it("여러 상품을 한 번에 주문할 수 있다") {
                    // given
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(
                            OrderItemCommand(product1Id, 1),
                            OrderItemCommand(product2Id, 2)
                        ),
                        couponId = null
                    )

                    // when
                    val orderResponse = orderService.createOrder(command)

                    // then
                    orderResponse.items.size shouldBe 2
                    orderResponse.pricing.totalAmount shouldBe 160000L // 100000 + (30000 * 2)
                    orderResponse.pricing.finalAmount shouldBe 160000L

                    // then - 각 상품의 재고 차감 검증
                    val updatedProduct1 = productService.findProductById(product1Id)
                    val updatedProduct2 = productService.findProductById(product2Id)
                    updatedProduct1.stock shouldBe 9 // 10 - 1
                    updatedProduct2.stock shouldBe 18 // 20 - 2
                }
            }

            context("주문 취소") {
                it("주문을 취소하면 재고가 복원된다") {
                    // given - 주문 생성
                    val createCommand = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 3)),
                        couponId = null
                    )
                    val orderResponse = orderService.createOrder(createCommand)

                    // 재고 차감 확인
                    val productAfterOrder = productService.findProductById(product1Id)
                    productAfterOrder.stock shouldBe 7 // 10 - 3

                    // when - 주문 취소
                    val cancelCommand = CancelOrderCommand(testUserId)
                    val cancelResponse = orderService.cancelOrder(orderResponse.orderId, cancelCommand)

                    // then
                    cancelResponse.status shouldBe "CANCELLED"
                    cancelResponse.refund.restoredStock.size shouldBe 1
                    cancelResponse.refund.restoredStock[0].productId shouldBe product1Id
                    cancelResponse.refund.restoredStock[0].quantity shouldBe 3

                    // then - 재고 복원 검증
                    val productAfterCancel = productService.findProductById(product1Id)
                    productAfterCancel.stock shouldBe 10 // 7 + 3 = 10 (원래대로 복원)
                }

                it("쿠폰 사용한 주문을 취소하면 쿠폰이 복원된다") {
                    // given - 쿠폰 발급
                    val issueCouponCommand = IssueCouponCommand(userId = testUserId)
                    val issuedCoupon = couponService.issueCoupon(couponId, issueCouponCommand)

                    // given - 쿠폰 사용하여 주문 생성
                    val createCommand = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 1)),
                        couponId = couponId
                    )
                    val orderResponse = orderService.createOrder(createCommand)

                    // 쿠폰 사용됨 확인
                    val usedCoupons = couponService.getUserCoupons(testUserId, CouponStatus.USED)
                    usedCoupons.coupons.size shouldBe 1

                    // when - 주문 취소
                    val cancelCommand = CancelOrderCommand(testUserId)
                    val cancelResponse = orderService.cancelOrder(orderResponse.orderId, cancelCommand)

                    // then
                    cancelResponse.status shouldBe "CANCELLED"
                    cancelResponse.refund.restoredCoupon shouldNotBe null
                    cancelResponse.refund.restoredCoupon?.couponId shouldBe couponId

                    // then - 쿠폰 복원 검증
                    val availableCoupons = couponService.getUserCoupons(testUserId, CouponStatus.AVAILABLE)
                    availableCoupons.coupons.size shouldBe 1
                    availableCoupons.coupons[0].status shouldBe "AVAILABLE"
                }
            }

            context("재고 부족 예외") {
                it("재고가 부족한 경우 주문이 실패한다") {
                    // given
                    val command = CreateOrderCommand(
                        userId = testUserId,
                        items = listOf(OrderItemCommand(product1Id, 100)), // 재고 10개인데 100개 주문
                        couponId = null
                    )

                    // when & then
                    shouldThrow<InsufficientStockException> {
                        orderService.createOrder(command)
                    }

                    // then - 재고가 차감되지 않았는지 확인
                    val product = productService.findProductById(product1Id)
                    product.stock shouldBe 10 // 변화 없음
                }
            }
        }
    }
}
