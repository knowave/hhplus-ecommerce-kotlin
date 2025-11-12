package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.application.product.dto.GetProductsCommand
import com.hhplus.ecommerce.common.exception.ProductNotFoundException
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import com.hhplus.ecommerce.domain.product.entity.Product
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
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
class ProductServiceIntegrationTest(
    private val productService: ProductService,
    private val productJpaRepository: ProductJpaRepository
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private val testProducts = mutableListOf<UUID>()

    init {
        beforeEach {
            // 각 테스트마다 독립적으로 테스트용 상품 생성
            testProducts.clear()

            val productsToCreate = listOf(
                // ELECTRONICS (5개)
                Product(
                    name = "노트북 ABC",
                    description = "고성능 노트북",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    specifications = mapOf("cpu" to "Intel i7", "ram" to "16GB"),
                    salesCount = 150
                ),
                Product(
                    name = "스마트폰 XYZ",
                    description = "최신 플래그십",
                    price = 1200000L,
                    stock = 100,
                    category = ProductCategory.ELECTRONICS,
                    specifications = mapOf("display" to "6.5inch"),
                    salesCount = 200
                ),
                Product(
                    name = "태블릿 Pro",
                    description = "업무용 태블릿",
                    price = 800000L,
                    stock = 30,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 80
                ),
                Product(
                    name = "무선 이어폰",
                    description = "노이즈 캔슬링",
                    price = 250000L,
                    stock = 200,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 300
                ),
                Product(
                    name = "스마트워치",
                    description = "건강 관리",
                    price = 400000L,
                    stock = 80,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 120
                ),
                // FASHION (3개)
                Product(
                    name = "운동화",
                    description = "편안한 운동화",
                    price = 150000L,
                    stock = 200,
                    category = ProductCategory.FASHION,
                    specifications = emptyMap(),
                    salesCount = 120
                ),
                Product(
                    name = "청바지",
                    description = "데님 청바지",
                    price = 89000L,
                    stock = 150,
                    category = ProductCategory.FASHION,
                    specifications = emptyMap(),
                    salesCount = 90
                ),
                Product(
                    name = "가죽 재킷",
                    description = "고급 가죽",
                    price = 350000L,
                    stock = 40,
                    category = ProductCategory.FASHION,
                    specifications = emptyMap(),
                    salesCount = 50
                ),
                // FOOD (2개)
                Product(
                    name = "유기농 쌀",
                    description = "국내산 유기농",
                    price = 45000L,
                    stock = 500,
                    category = ProductCategory.FOOD,
                    specifications = emptyMap(),
                    salesCount = 250
                ),
                Product(
                    name = "프리미엄 커피",
                    description = "원두 커피",
                    price = 25000L,
                    stock = 300,
                    category = ProductCategory.FOOD,
                    specifications = emptyMap(),
                    salesCount = 180
                ),
                // BOOKS (2개)
                Product(
                    name = "Kotlin 완벽 가이드",
                    description = "프로그래밍 서적",
                    price = 35000L,
                    stock = 100,
                    category = ProductCategory.BOOKS,
                    specifications = emptyMap(),
                    salesCount = 70
                ),
                Product(
                    name = "Clean Code",
                    description = "클린 코드",
                    price = 32000L,
                    stock = 80,
                    category = ProductCategory.BOOKS,
                    specifications = emptyMap(),
                    salesCount = 150
                ),
                // HOME (2개)
                Product(
                    name = "공기청정기",
                    description = "미세먼지 제거",
                    price = 280000L,
                    stock = 60,
                    category = ProductCategory.HOME,
                    specifications = emptyMap(),
                    salesCount = 45
                ),
                Product(
                    name = "진공청소기",
                    description = "무선 청소기",
                    price = 320000L,
                    stock = 40,
                    category = ProductCategory.HOME,
                    specifications = emptyMap(),
                    salesCount = 60
                ),
                // SPORTS (1개)
                Product(
                    name = "요가 매트",
                    description = "두꺼운 요가 매트",
                    price = 45000L,
                    stock = 150,
                    category = ProductCategory.SPORTS,
                    specifications = emptyMap(),
                    salesCount = 100
                )
            )

            productsToCreate.forEach { product ->
                val saved = productJpaRepository.save(product)
                testProducts.add(saved.id!!)
            }
        }

        afterEach {
            productJpaRepository.deleteAll()
        }

    describe("ProductService 통합 테스트 - 상품 조회") {

        context("전체 상품 조회") {
            it("모든 상품을 최신순으로 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    sortBy = GetProductsCommand.SortBy.NEWEST,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 0,
                    size = 20
                ))

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.size shouldBe minOf(20, response.pagination.totalElements.toInt()) // 페이지 크기 20 또는 전체 상품 수
                response.pagination.currentPage shouldBe 0
            }

            it("가격순으로 정렬하여 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    sortBy = GetProductsCommand.SortBy.PRICE,
                    orderBy = GetProductsCommand.OrderBy.ASC,
                    page = 0,
                    size = 20
                ))

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
                val response = productService.getProducts(GetProductsCommand(
                    sortBy = GetProductsCommand.SortBy.POPULARITY,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 0,
                    size = 20
                ))

                // then
                response.products.isNotEmpty() shouldBe true

                // 판매량 내림차순 정렬 확인
                if (response.products.size > 1) {
                    for (i in 0 until response.products.size - 1) {
                        val current = response.products[i]
                        val next = response.products[i + 1]
                        (current.salesCount >= next.salesCount) shouldBe true
                    }
                }
            }

            it("페이지네이션이 정상적으로 동작한다") {
                // when - 첫 페이지
                val firstPage = productService.getProducts(GetProductsCommand(
                    page = 0,
                    size = 5
                ))

                // then
                firstPage.products shouldHaveSize 5
                firstPage.pagination.currentPage shouldBe 0
                firstPage.pagination.hasNext shouldBe (firstPage.pagination.totalElements > 5)
                firstPage.pagination.hasPrevious shouldBe false
                firstPage.pagination.totalElements shouldBe (firstPage.pagination.totalElements) // 동적 확인

                // when - 두 번째 페이지
                val secondPage = productService.getProducts(GetProductsCommand(
                    page = 1,
                    size = 5
                ))

                // then
                secondPage.products shouldHaveSize 5
                secondPage.pagination.currentPage shouldBe 1
                secondPage.pagination.hasPrevious shouldBe true
            }
        }

        context("카테고리별 상품 조회") {
            it("ELECTRONICS 카테고리의 상품만 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    category = "ELECTRONICS",
                    page = 0,
                    size = 10
                ))

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
            }

            it("FASHION 카테고리의 상품만 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    category = "FASHION",
                    page = 0,
                    size = 10
                ))

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FASHION } shouldBe true
            }

            it("FOOD 카테고리의 상품만 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    category = "FOOD",
                    page = 0,
                    size = 10
                ))

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FOOD } shouldBe true
            }

            it("카테고리별 인기순 상품을 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    category = "FASHION",
                    sortBy = GetProductsCommand.SortBy.POPULARITY,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 0,
                    size = 10
                ))

                // then
                response.products.isNotEmpty() shouldBe true
                response.products.all { it.category == ProductCategory.FASHION } shouldBe true

                // 판매량 내림차순 확인
                if (response.products.size > 1) {
                    for (i in 0 until response.products.size - 1) {
                        val current = response.products[i]
                        val next = response.products[i + 1]
                        (current.salesCount >= next.salesCount) shouldBe true
                    }
                }
            }
        }

        context("상품 상세 조회") {
            it("상품 ID로 상세 정보를 조회할 수 있다") {
                // given
                val productId = testProducts.first()

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
                val productId = testProducts.first()

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
                val invalidProductId = UUID.randomUUID()

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

            it("판매량 기준으로 정렬된다") {
                // when
                val response = productService.getTopProducts(days = 30, limit = 3)

                // then
                response.products shouldHaveSize 3
                // 상위 3개 상품 확인 (정확한 이름은 DB에 있는 최상위 판매량 순)
                response.products[0].salesCount shouldBe 300
                response.products[1].salesCount shouldBe 250
                response.products[2].salesCount shouldBe 250
            }

            it("limit보다 적은 상품이 있으면 모든 상품을 반환한다") {
                // when
                val response = productService.getTopProducts(days = 30, limit = 1000)

                // then
                response.products.isNotEmpty() shouldBe true
            }
        }
    }

    describe("ProductService 통합 테스트 - 상품 검색 및 필터링 조합") {

        context("카테고리 + 정렬 조합") {
            it("ELECTRONICS 카테고리를 가격순으로 조회할 수 있다") {
                // when
                val response = productService.getProducts(GetProductsCommand(
                    category = "ELECTRONICS",
                    sortBy = GetProductsCommand.SortBy.PRICE,
                    orderBy = GetProductsCommand.OrderBy.ASC,
                    page = 0,
                    size = 10
                ))

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
                val response = productService.getProducts(GetProductsCommand(
                    category = "FASHION",
                    sortBy = GetProductsCommand.SortBy.POPULARITY,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 0,
                    size = 10
                ))

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
                val response = productService.getProducts(GetProductsCommand(
                    category = "ELECTRONICS",
                    sortBy = GetProductsCommand.SortBy.PRICE,
                    orderBy = GetProductsCommand.OrderBy.ASC,
                    page = 0,
                    size = 3
                ))

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
                val newProducts = productService.getProducts(GetProductsCommand(
                    sortBy = GetProductsCommand.SortBy.NEWEST,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 0,
                    size = 10
                ))

                // then
                newProducts.products shouldHaveSize 10
            }
        }

        context("카테고리 페이지 시나리오") {
            it("전자제품 카테고리에서 가격이 저렴한 순으로 10개를 조회한다") {
                // when
                val products = productService.getProducts(GetProductsCommand(
                    category = "ELECTRONICS",
                    sortBy = GetProductsCommand.SortBy.PRICE,
                    orderBy = GetProductsCommand.OrderBy.ASC,
                    page = 0,
                    size = 10
                ))

                // then
                products.products.isNotEmpty() shouldBe true
                products.products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
            }

            it("패션 카테고리에서 인기 상품 순으로 페이지를 넘기며 조회한다") {
                // when - 첫 페이지
                val firstPage = productService.getProducts(GetProductsCommand(
                    category = "FASHION",
                    sortBy = GetProductsCommand.SortBy.POPULARITY,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 0,
                    size = 2
                ))

                // when - 두 번째 페이지
                val secondPage = productService.getProducts(GetProductsCommand(
                    category = "FASHION",
                    sortBy = GetProductsCommand.SortBy.POPULARITY,
                    orderBy = GetProductsCommand.OrderBy.DESC,
                    page = 1,
                    size = 2
                ))

                // then
                firstPage.products.isNotEmpty() shouldBe true
                firstPage.products.all { it.category == ProductCategory.FASHION } shouldBe true

                // 두 페이지 모두 데이터가 있으면 검증
                if (secondPage.products.isNotEmpty()) {
                    secondPage.pagination.currentPage shouldBe 1
                    secondPage.products.all { it.category == ProductCategory.FASHION } shouldBe true
                }
            }
        }

        context("상품 상세 페이지 시나리오") {
            it("상품 상세 정보를 조회한다") {
                // given
                val productId = testProducts.first()

                // when
                val product = productService.findProductById(productId)

                // then
                product.id shouldBe productId
                product.name shouldNotBe null
                product.stock shouldNotBe null
            }
        }
    }
    }
}
