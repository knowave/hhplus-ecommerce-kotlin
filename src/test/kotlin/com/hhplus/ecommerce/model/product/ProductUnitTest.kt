package com.hhplus.ecommerce.model.product

import com.hhplus.ecommerce.infrastructure.product.ProductCategory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ProductUnitTest : DescribeSpec({
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    describe("Product 도메인 모델 단위 테스트") {
        context("Product 객체 생성") {
            it("Product 객체가 정상적으로 생성된다") {
                // given
                val productId = 1L
                val productName = "노트북 ABC"
                val description = "고성능 노트북"
                val price = 1500000L
                val stock = 50
                val category = ProductCategory.ELECTRONICS
                val specifications = mapOf("cpu" to "Intel i7", "ram" to "16GB")
                val salesCount = 150
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val product = Product(
                    id = productId,
                    name = productName,
                    description = description,
                    price = price,
                    stock = stock,
                    category = category,
                    specifications = specifications,
                    salesCount = salesCount,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                product shouldNotBe null
                product.id shouldBe productId
                product.name shouldBe productName
                product.description shouldBe description
                product.price shouldBe price
                product.stock shouldBe stock
                product.category shouldBe category
                product.specifications shouldBe specifications
                product.salesCount shouldBe salesCount
                product.createdAt shouldBe now
                product.updatedAt shouldBe now
            }

            it("specifications가 빈 맵일 때도 정상적으로 생성된다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val product = Product(
                    id = 1L,
                    name = "간단한 상품",
                    description = "설명",
                    price = 10000L,
                    stock = 10,
                    category = ProductCategory.HOME,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                product.specifications shouldBe emptyMap()
            }

            it("salesCount가 0인 새로운 상품을 생성할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val product = Product(
                    id = 1L,
                    name = "새 상품",
                    description = "신규 출시",
                    price = 50000L,
                    stock = 100,
                    category = ProductCategory.FASHION,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                product.salesCount shouldBe 0
            }

            it("재고가 0인 품절 상품을 생성할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val product = Product(
                    id = 1L,
                    name = "품절 상품",
                    description = "재고 없음",
                    price = 30000L,
                    stock = 0,
                    category = ProductCategory.FOOD,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                product.stock shouldBe 0
            }
        }

        context("Product 속성 변경") {
            it("Product의 재고를 변경할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    createdAt = now,
                    updatedAt = now
                )
                val newStock = 45

                // when
                product.stock = newStock

                // then
                product.stock shouldBe newStock
            }

            it("Product의 판매량을 변경할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    salesCount = 100,
                    createdAt = now,
                    updatedAt = now
                )
                val newSalesCount = 150

                // when
                product.salesCount = newSalesCount

                // then
                product.salesCount shouldBe newSalesCount
            }

            it("Product의 updatedAt을 변경할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                Thread.sleep(10)
                val newUpdatedAt = LocalDateTime.now().format(dateFormatter)
                product.updatedAt = newUpdatedAt

                // then
                product.updatedAt shouldNotBe now
                product.updatedAt shouldBe newUpdatedAt
            }

            it("재고 차감 시나리오: 주문으로 재고가 감소한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    createdAt = now,
                    updatedAt = now
                )
                val orderQuantity = 5

                // when
                product.stock = product.stock - orderQuantity

                // then
                product.stock shouldBe 45
            }

            it("판매량 증가 시나리오: 주문 완료 시 판매량이 증가한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    salesCount = 100,
                    createdAt = now,
                    updatedAt = now
                )
                val soldQuantity = 3

                // when
                product.salesCount = product.salesCount + soldQuantity

                // then
                product.salesCount shouldBe 103
            }
        }

        context("ProductCategory 열거형 동작") {
            it("모든 카테고리를 조회할 수 있다") {
                // when
                val categories = ProductCategory.entries

                // then
                categories.size shouldBe 7
                categories shouldBe listOf(
                    ProductCategory.ELECTRONICS,
                    ProductCategory.FASHION,
                    ProductCategory.FOOD,
                    ProductCategory.BOOKS,
                    ProductCategory.HOME,
                    ProductCategory.SPORTS,
                    ProductCategory.BEAUTY
                )
            }

            it("문자열로 ProductCategory를 생성할 수 있다") {
                // when
                val category = ProductCategory.valueOf("ELECTRONICS")

                // then
                category shouldBe ProductCategory.ELECTRONICS
            }

            it("카테고리의 이름을 문자열로 가져올 수 있다") {
                // given
                val category = ProductCategory.ELECTRONICS

                // when
                val categoryName = category.name

                // then
                categoryName shouldBe "ELECTRONICS"
            }
        }

        context("data class 동작") {
            it("동일한 값을 가진 Product 객체는 같다고 판단된다 (equals)") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product1 = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    createdAt = now,
                    updatedAt = now
                )
                val product2 = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                product1 shouldBe product2
            }

            it("copy() 메서드로 일부 속성만 변경한 새 객체를 생성할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    salesCount = 100,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                val copiedProduct = product.copy(stock = 45, salesCount = 105)

                // then
                copiedProduct.id shouldBe product.id
                copiedProduct.name shouldBe product.name
                copiedProduct.price shouldBe product.price
                copiedProduct.stock shouldBe 45
                copiedProduct.salesCount shouldBe 105
                copiedProduct.createdAt shouldBe product.createdAt
                copiedProduct shouldNotBe product // stock과 salesCount가 다르므로
            }
        }

        context("비즈니스 시나리오 테스트") {
            it("상품 주문 시 재고 차감과 판매량 증가가 함께 발생한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    salesCount = 100,
                    createdAt = now,
                    updatedAt = now
                )
                val orderQuantity = 5

                // when - 주문 처리
                product.stock = product.stock - orderQuantity
                product.salesCount = product.salesCount + orderQuantity
                val newUpdatedAt = LocalDateTime.now().format(dateFormatter)
                product.updatedAt = newUpdatedAt

                // then
                product.stock shouldBe 45
                product.salesCount shouldBe 105
                product.updatedAt shouldNotBe now
            }

            it("재고가 0이 되면 품절 상태가 된다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 5,
                    category = ProductCategory.ELECTRONICS,
                    createdAt = now,
                    updatedAt = now
                )

                // when - 남은 재고 모두 판매
                product.stock = product.stock - 5

                // then
                product.stock shouldBe 0
            }

            it("주문 취소 시 재고가 복원된다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1L,
                    name = "노트북",
                    description = "고성능",
                    price = 1500000L,
                    stock = 45,
                    category = ProductCategory.ELECTRONICS,
                    salesCount = 105,
                    createdAt = now,
                    updatedAt = now
                )
                val cancelQuantity = 5

                // when - 주문 취소 처리
                product.stock = product.stock + cancelQuantity
                product.salesCount = product.salesCount - cancelQuantity

                // then
                product.stock shouldBe 50
                product.salesCount shouldBe 100
            }
        }
    }
})
