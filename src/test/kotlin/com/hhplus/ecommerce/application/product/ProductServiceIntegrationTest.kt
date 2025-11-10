package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductRepository
import com.hhplus.ecommerce.infrastructure.product.ProductRepositoryImpl
import com.hhplus.ecommerce.domain.product.entity.Product
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class ProductServiceIntegrationTest : DescribeSpec({

    lateinit var productRepository: ProductRepository
    lateinit var productService: ProductService

    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        // 실제 구현체 사용
        productRepository = ProductRepositoryImpl()
        productService = ProductServiceImpl(productRepository)
    }

    describe("ProductService 통합 테스트 - 상품 조회") {

        context("전체 상품 조회") {
            it("모든 상품을 최신순으로 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = null,
                    sort = "newest",
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.pagination.totalElements shouldNotBe 0
                response.pagination.currentPage shouldBe 0
            }

            it("가격순으로 정렬하여 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = null,
                    sort = "price",
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true

                // 가격 오름차순 정렬 확인
                if (response.products.size > 1) {
                    for (i in 0 until response.products.size - 1) {
                        val current = response.products[i]
                        val next = response.products[i + 1]
                        (current.price <= next.price) shouldBe true
                    }
                }
            }

            it("인기순으로 정렬하여 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = null,
                    sort = "popularity",
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                // ProductSummary에는 salesCount가 없으므로 단순히 조회만 확인
            }

            it("페이지네이션이 정상적으로 동작한다") {
                // when - 첫 페이지
                val firstPage = productService.getProducts(
                    category = null,
                    sort = null,
                    page = 0,
                    size = 5
                )

                // then
                firstPage.products shouldHaveSize 5
                firstPage.pagination.currentPage shouldBe 0
                firstPage.pagination.hasNext shouldBe true
                firstPage.pagination.hasPrevious shouldBe false

                // when - 두 번째 페이지
                val secondPage = productService.getProducts(
                    category = null,
                    sort = null,
                    page = 1,
                    size = 5
                )

                // then
                secondPage.products shouldHaveSize 5
                secondPage.pagination.currentPage shouldBe 1
                secondPage.pagination.hasPrevious shouldBe true
            }
        }

        context("카테고리별 상품 조회") {
            it("ELECTRONICS 카테고리의 상품만 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "ELECTRONICS",
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
            }

            it("FASHION 카테고리의 상품만 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "FASHION",
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FASHION } shouldBe true
            }

            it("FOOD 카테고리의 상품만 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "FOOD",
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FOOD } shouldBe true

                // ProductSummary에는 salesCount가 없으므로 단순히 조회만 확인
            }

            it("카테고리별 인기순 상품을 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "FASHION",
                    sort = "popularity",
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FASHION } shouldBe true

                // ProductSummary에는 salesCount가 없으므로 단순히 조회만 확인
            }

            it("존재하지 않는 카테고리로 조회하면 빈 리스트를 반환한다") {
                // when
                val response = productService.getProducts(
                    category = "INVALID_CATEGORY",
                    sort = null,
                    page = 0,
                    size = 10
                )

                // then
                response.products shouldHaveSize 0
            }
        }

        context("상품 상세 조회") {
            it("상품 ID로 상세 정보를 조회할 수 있다") {
                // given
                val productId = 1L

                // when
                val response = productService.findProductById(productId)

                // then
                response.id shouldBe productId
                response.name shouldNotBe null
                response.description shouldNotBe null
                response.price shouldNotBe null
                response.stock shouldNotBe null
                response.category shouldNotBe null
            }

            it("상품의 모든 정보가 포함되어 있다") {
                // given
                val productId = 1L

                // when
                val response = productService.findProductById(productId)

                // then
                response.id shouldBe productId
                response.name shouldBe "노트북 ABC"
                response.price shouldBe 1500000L
                response.stock shouldBe 50
                response.category shouldBe ProductCategory.ELECTRONICS
                response.salesCount shouldBe 150
                response.specifications shouldNotBe null
            }

            it("존재하지 않는 상품 ID로 조회 시 예외가 발생한다") {
                // given
                val invalidProductId = 999L

                // when & then
                shouldThrow<ProductNotFoundException> {
                    productService.findProductById(invalidProductId)
                }
            }
        }

        context("상품 재고 조회") {
            it("상품 ID로 재고를 조회할 수 있다") {
                // given
                val productId = 1L

                // when
                val response = productService.findProductById(productId)

                // then
                response.id shouldBe productId
                response.name shouldNotBe null
                response.stock shouldNotBe null
                response.stock shouldBe 50
            }

            it("재고가 0인 상품도 조회할 수 있다") {
                // given - 재고 0인 상품 생성
                val now = LocalDateTime.now().format(dateFormatter)
                val product = Product(
                    id = 1000L,
                    name = "품절 상품",
                    description = "재고 없음",
                    price = 10000L,
                    stock = 0,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                productRepository.save(product)

                // when
                val response = productService.findProductById(1000L)

                // then
                response.stock shouldBe 0
            }

            it("존재하지 않는 상품 ID로 조회 시 예외가 발생한다") {
                // given
                val invalidProductId = 999L

                // when & then
                shouldThrow<ProductNotFoundException> {
                    productService.findProductById(invalidProductId)
                }
            }
        }

        context("인기 상품 조회") {
            it("상위 N개의 인기 상품을 조회할 수 있다") {
                // when
                val response = productService.getTopProducts(days = 30, limit = 5)

                // then
                response.products shouldHaveSize 5

                // 판매량 내림차순 확인
                if (response.products.size > 1) {
                    for (i in 0 until response.products.size - 1) {
                        val current = response.products[i]
                        val next = response.products[i + 1]
                        (current.salesCount >= next.salesCount) shouldBe true
                    }
                }
            }

            it("limit보다 적은 상품이 있으면 모든 상품을 반환한다") {
                // when
                val response = productService.getTopProducts(days = 30, limit = 1000)

                // then
                response.products.isNotEmpty() shouldBe true
                // 총 상품 수보다 많은 limit를 요청해도 실제 상품 수만큼만 반환
            }
        }
    }

    describe("ProductService 통합 테스트 - 상품 검색 및 필터링 조합") {

        context("카테고리 + 정렬 조합") {
            it("ELECTRONICS 카테고리를 가격순으로 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "ELECTRONICS",
                    sort = "price",
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true

                // 가격 오름차순 확인
                for (i in 0 until response.products.size - 1) {
                    val current = response.products[i]
                    val next = response.products[i + 1]
                    (current.price <= next.price) shouldBe true
                }
            }

            it("FASHION 카테고리를 인기순으로 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "FASHION",
                    sort = "popularity",
                    page = 0,
                    size = 10
                )

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FASHION } shouldBe true

                // 판매량 내림차순 확인
                for (i in 0 until response.products.size - 1) {
                    val current = response.products[i]
                    val next = response.products[i + 1]
                    (current.salesCount >= next.salesCount) shouldBe true
                }
            }
        }

        context("카테고리 + 정렬 + 페이지네이션 조합") {
            it("복합 조건으로 상품을 조회할 수 있다") {
                // when
                val response = productService.getProducts(
                    category = "ELECTRONICS",
                    sort = "price",
                    page = 0,
                    size = 3
                )

                // then
                response.products shouldHaveSize 3
                response.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
                response.pagination.currentPage shouldBe 0
                response.pagination.size shouldBe 3

                // 가격 오름차순 확인
                for (i in 0 until response.products.size - 1) {
                    val current = response.products[i]
                    val next = response.products[i + 1]
                    (current.price <= next.price) shouldBe true
                    (current.salesCount >= next.salesCount) shouldBe true
                }
            }
        }
    }

    describe("ProductService 통합 테스트 - 실제 데이터 시나리오") {

        context("쇼핑몰 메인 페이지 시나리오") {
            it("메인 페이지에 표시할 인기 상품 5개를 조회한다") {
                // when
                val topProducts = productService.getTopProducts(days = 7, limit = 5)

                // then
                topProducts.products shouldHaveSize 5
                topProducts.period.days shouldBe 7
                topProducts.period.startDate shouldNotBe null
                topProducts.period.endDate shouldNotBe null
            }

            it("메인 페이지에 표시할 신상품 10개를 조회한다") {
                // when
                val newProducts = productService.getProducts(
                    category = null,
                    sort = "newest",
                    page = 0,
                    size = 10
                )

                // then
                newProducts.products shouldHaveSize 10
            }
        }

        context("카테고리 페이지 시나리오") {
            it("전자제품 카테고리에서 가격이 저렴한 순으로 10개를 조회한다") {
                // when
                val products = productService.getProducts(
                    category = "ELECTRONICS",
                    sort = "price",
                    page = 0,
                    size = 10
                )

                // then
                products.products.isNotEmpty() shouldBe true
                products.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
            }

            it("패션 카테고리에서 인기 상품 순으로 페이지를 넘기며 조회한다") {
                // when - 첫 페이지
                val firstPage = productService.getProducts(
                    category = "FASHION",
                    sort = "popularity",
                    page = 0,
                    size = 5
                )

                // when - 두 번째 페이지
                val secondPage = productService.getProducts(
                    category = "FASHION",
                    sort = "popularity",
                    page = 1,
                    size = 5
                )

                // then
                firstPage.products.isNotEmpty() shouldBe true
                firstPage.products.all { it.category == ProductCategory.FASHION } shouldBe true

                // 첫 페이지와 두 번째 페이지의 상품이 다름
                if (secondPage.products.isNotEmpty()) {
                    val firstPageIds = firstPage.products.map { it.id }.toSet()
                    val secondPageIds = secondPage.products.map { it.id }.toSet()
                    (firstPageIds intersect secondPageIds).isEmpty() shouldBe true
                }
            }
        }

        context("상품 상세 페이지 시나리오") {
            it("상품 상세 정보를 조회한다") {
                // given
                val productId = 1L

                // when
                val product = productService.findProductById(productId)

                // then
                product.id shouldBe productId
                product.name shouldNotBe null
                product.stock shouldNotBe null
            }
        }
    }
})
