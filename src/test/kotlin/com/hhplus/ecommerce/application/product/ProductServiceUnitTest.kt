package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ProductServiceUnitTest : DescribeSpec({
    lateinit var productRepository: ProductRepository
    lateinit var productService: ProductServiceImpl
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        productRepository = mockk()
        productService = ProductServiceImpl(productRepository)
    }

    describe("ProductService 단위 테스트 - getProducts") {
        context("정상 케이스 - 전체 상품 조회") {
            it("모든 상품을 최신순으로 조회한다 (기본 정렬)") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.ELECTRONICS, 50, "2025-10-01T00:00:00", now),
                    createProduct(2L, "상품2", 20000L, 200, ProductCategory.FASHION, 30, "2025-10-05T00:00:00", now),
                    createProduct(3L, "상품3", 15000L, 150, ProductCategory.FOOD, 70, "2025-10-10T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 3
                result.products[0].id shouldBe 3L // 최신순이므로 가장 최근 상품이 먼저
                result.products[1].id shouldBe 2L
                result.products[2].id shouldBe 1L
                result.pagination.totalElements shouldBe 3
                result.pagination.totalPages shouldBe 1
                result.pagination.currentPage shouldBe 0
                result.pagination.hasNext shouldBe false
                result.pagination.hasPrevious shouldBe false

                verify(exactly = 1) { productRepository.findAll() }
            }
        }

        context("정상 케이스 - 카테고리 필터링") {
            it("특정 카테고리의 상품만 조회한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val electronicsProducts = listOf(
                    createProduct(1L, "노트북", 1500000L, 50, ProductCategory.ELECTRONICS, 100, "2025-10-01T00:00:00", now),
                    createProduct(2L, "스마트폰", 1200000L, 100, ProductCategory.ELECTRONICS, 150, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findByCategory(ProductCategory.ELECTRONICS) } returns electronicsProducts

                // when
                val result = productService.getProducts(
                    category = "ELECTRONICS",
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 2
                result.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true

                verify(exactly = 1) { productRepository.findByCategory(ProductCategory.ELECTRONICS) }
                verify(exactly = 0) { productRepository.findAll() }
            }

            it("소문자 카테고리명도 대문자로 변환하여 조회한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val fashionProducts = listOf(
                    createProduct(3L, "청바지", 79000L, 200, ProductCategory.FASHION, 120, "2025-10-03T00:00:00", now)
                )

                every { productRepository.findByCategory(ProductCategory.FASHION) } returns fashionProducts

                // when
                val result = productService.getProducts(
                    category = "fashion", // 소문자
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 1
                result.products[0].category shouldBe ProductCategory.FASHION

                verify(exactly = 1) { productRepository.findByCategory(ProductCategory.FASHION) }
            }
        }

        context("정상 케이스 - 정렬") {
            val now = LocalDateTime.now().format(dateFormatter)
            val products = listOf(
                createProduct(1L, "상품1", 30000L, 100, ProductCategory.ELECTRONICS, 50, "2025-10-01T00:00:00", now),
                createProduct(2L, "상품2", 10000L, 200, ProductCategory.FASHION, 200, "2025-10-05T00:00:00", now),
                createProduct(3L, "상품3", 20000L, 150, ProductCategory.FOOD, 100, "2025-10-10T00:00:00", now)
            )

            it("가격순으로 정렬한다 (오름차순)") {
                // given
                every { productRepository.findAll() } returns products

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = "price",
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 3
                result.products[0].price shouldBe 10000L
                result.products[1].price shouldBe 20000L
                result.products[2].price shouldBe 30000L

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("인기순으로 정렬한다 (판매량 내림차순)") {
                // given
                every { productRepository.findAll() } returns products

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = "popularity",
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 3
                result.products[0].id shouldBe 2L // 판매량 200
                result.products[1].id shouldBe 3L // 판매량 100
                result.products[2].id shouldBe 1L // 판매량 50

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("최신순으로 정렬한다 (명시적으로 newest 지정)") {
                // given
                every { productRepository.findAll() } returns products

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = "newest",
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 3
                result.products[0].id shouldBe 3L // 가장 최근
                result.products[1].id shouldBe 2L
                result.products[2].id shouldBe 1L

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("알 수 없는 정렬 옵션은 기본값(최신순)으로 처리한다") {
                // given
                every { productRepository.findAll() } returns products

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = "unknown_sort",
                    page = 0,
                    size = 10
                )

                // then
                result.products shouldHaveSize 3
                result.products[0].id shouldBe 3L // 최신순

                verify(exactly = 1) { productRepository.findAll() }
            }
        }

        context("정상 케이스 - 페이지네이션") {
            val now = LocalDateTime.now().format(dateFormatter)
            val manyProducts = (1..15).map { i ->
                createProduct(
                    id = i.toLong(),
                    name = "상품$i",
                    price = 10000L * i,
                    stock = 100,
                    category = ProductCategory.ELECTRONICS,
                    salesCount = 0,
                    createdAt = "2025-10-${String.format("%02d", i)}T00:00:00",
                    updatedAt = now
                )
            }

            it("첫 페이지를 정상적으로 조회한다") {
                // given
                every { productRepository.findAll() } returns manyProducts

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = null,
                    page = 0,
                    size = 5
                )

                // then
                result.products shouldHaveSize 5
                result.pagination.currentPage shouldBe 0
                result.pagination.totalPages shouldBe 3 // 15개 / 5 = 3 페이지
                result.pagination.totalElements shouldBe 15
                result.pagination.hasNext shouldBe true
                result.pagination.hasPrevious shouldBe false

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("중간 페이지를 정상적으로 조회한다") {
                // given
                every { productRepository.findAll() } returns manyProducts

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = null,
                    page = 1,
                    size = 5
                )

                // then
                result.products shouldHaveSize 5
                result.pagination.currentPage shouldBe 1
                result.pagination.hasNext shouldBe true
                result.pagination.hasPrevious shouldBe true

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("마지막 페이지를 정상적으로 조회한다") {
                // given
                every { productRepository.findAll() } returns manyProducts

                // when
                val result = productService.getProducts(
                    category = null,
                    sort = null,
                    page = 2,
                    size = 5
                )

                // then
                result.products shouldHaveSize 5
                result.pagination.currentPage shouldBe 2
                result.pagination.hasNext shouldBe false
                result.pagination.hasPrevious shouldBe true

                verify(exactly = 1) { productRepository.findAll() }
            }
        }
    }

    describe("ProductService 단위 테스트 - getTopProducts") {
        context("정상 케이스") {
            it("판매량 기준으로 상위 상품을 조회한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.ELECTRONICS, 50, "2025-10-01T00:00:00", now),
                    createProduct(2L, "상품2", 20000L, 200, ProductCategory.FASHION, 200, "2025-10-05T00:00:00", now),
                    createProduct(3L, "상품3", 15000L, 150, ProductCategory.FOOD, 150, "2025-10-10T00:00:00", now),
                    createProduct(4L, "상품4", 30000L, 50, ProductCategory.BOOKS, 100, "2025-10-15T00:00:00", now),
                    createProduct(5L, "상품5", 25000L, 80, ProductCategory.HOME, 0, "2025-10-20T00:00:00", now) // 판매량 0
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 3)

                // then
                result.products shouldHaveSize 3
                result.products[0].id shouldBe 2L // 판매량 200
                result.products[0].rank shouldBe 1
                result.products[0].salesCount shouldBe 200
                result.products[1].id shouldBe 3L // 판매량 150
                result.products[1].rank shouldBe 2
                result.products[2].id shouldBe 4L // 판매량 100
                result.products[2].rank shouldBe 3
                result.period.days shouldBe 3

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("판매량이 0인 상품은 제외된다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "판매된 상품", 10000L, 100, ProductCategory.ELECTRONICS, 50, "2025-10-01T00:00:00", now),
                    createProduct(2L, "안 팔린 상품", 20000L, 200, ProductCategory.FASHION, 0, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 5)

                // then
                result.products shouldHaveSize 1
                result.products[0].id shouldBe 1L
                result.products[0].salesCount shouldBe 50

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("판매량이 같을 때 매출액(가격 * 판매량)으로 정렬한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "저가 상품", 10000L, 100, ProductCategory.ELECTRONICS, 100, "2025-10-01T00:00:00", now),
                    createProduct(2L, "고가 상품", 50000L, 100, ProductCategory.FASHION, 100, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 5)

                // then
                result.products shouldHaveSize 2
                result.products[0].id shouldBe 2L // 매출액 5,000,000
                result.products[0].revenue shouldBe 5000000L
                result.products[1].id shouldBe 1L // 매출액 1,000,000
                result.products[1].revenue shouldBe 1000000L

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("판매량과 매출액이 같을 때 상품 ID로 정렬한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(3L, "상품3", 10000L, 100, ProductCategory.ELECTRONICS, 100, "2025-10-01T00:00:00", now),
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.FASHION, 100, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 5)

                // then
                result.products shouldHaveSize 2
                result.products[0].id shouldBe 1L // 더 작은 ID
                result.products[1].id shouldBe 3L

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("limit보다 적은 상품만 있어도 정상 처리한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.ELECTRONICS, 50, "2025-10-01T00:00:00", now),
                    createProduct(2L, "상품2", 20000L, 200, ProductCategory.FASHION, 100, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 5)

                // then
                result.products shouldHaveSize 2

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("판매된 상품이 없으면 빈 목록을 반환한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.ELECTRONICS, 0, "2025-10-01T00:00:00", now),
                    createProduct(2L, "상품2", 20000L, 200, ProductCategory.FASHION, 0, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 5)

                // then
                result.products.shouldBeEmpty()

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("period 정보가 정확하게 설정된다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.ELECTRONICS, 50, "2025-10-01T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 7, limit = 5)

                // then
                result.period.days shouldBe 7
                result.period.startDate shouldNotBe null
                result.period.endDate shouldNotBe null

                verify(exactly = 1) { productRepository.findAll() }
            }
        }

        context("엣지 케이스") {
            it("limit이 1이어도 정상 처리한다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val products = listOf(
                    createProduct(1L, "상품1", 10000L, 100, ProductCategory.ELECTRONICS, 100, "2025-10-01T00:00:00", now),
                    createProduct(2L, "상품2", 20000L, 200, ProductCategory.FASHION, 50, "2025-10-05T00:00:00", now)
                )

                every { productRepository.findAll() } returns products

                // when
                val result = productService.getTopProducts(days = 3, limit = 1)

                // then
                result.products shouldHaveSize 1
                result.products[0].id shouldBe 1L

                verify(exactly = 1) { productRepository.findAll() }
            }

            it("상품이 전혀 없어도 빈 목록을 반환한다") {
                // given
                every { productRepository.findAll() } returns emptyList()

                // when
                val result = productService.getTopProducts(days = 3, limit = 5)

                // then
                result.products.shouldBeEmpty()

                verify(exactly = 1) { productRepository.findAll() }
            }
        }
    }
}) {
    companion object {
        fun createProduct(
            id: Long,
            name: String,
            price: Long,
            stock: Int,
            category: ProductCategory,
            salesCount: Int,
            createdAt: String,
            updatedAt: String
        ): Product {
            return Product(
                id = id,
                name = name,
                description = "$name 상세 설명",
                price = price,
                stock = stock,
                category = category,
                specifications = emptyMap(),
                salesCount = salesCount,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
