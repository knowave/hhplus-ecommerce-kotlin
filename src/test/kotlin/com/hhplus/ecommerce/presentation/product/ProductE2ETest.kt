package com.hhplus.ecommerce.presentation.product

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import com.hhplus.ecommerce.presentation.product.dto.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val productJpaRepository: ProductJpaRepository
) : DescribeSpec({

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    val testProducts = mutableListOf<UUID>()

    beforeSpec {
        // E2E 테스트용 상품 데이터 생성 - 다양한 시나리오를 테스트할 수 있도록 충분한 데이터 생성
        val productsToCreate = listOf(
            // ELECTRONICS (5개)
            Product(
                name = "노트북 Pro",
                description = "고성능 노트북",
                price = 1800000L,
                stock = 50,
                category = ProductCategory.ELECTRONICS,
                specifications = mapOf("cpu" to "Intel i9", "ram" to "32GB"),
                salesCount = 180
            ),
            Product(
                name = "스마트폰 Galaxy",
                description = "최신 안드로이드 폰",
                price = 1300000L,
                stock = 100,
                category = ProductCategory.ELECTRONICS,
                specifications = mapOf("display" to "6.8inch"),
                salesCount = 250
            ),
            Product(
                name = "태블릿 Ultra",
                description = "업무용 태블릿",
                price = 900000L,
                stock = 40,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 90
            ),
            Product(
                name = "무선 이어폰 Pro",
                description = "노이즈 캔슬링 이어폰",
                price = 280000L,
                stock = 200,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 320
            ),
            Product(
                name = "스마트워치 Series 7",
                description = "건강 관리 워치",
                price = 450000L,
                stock = 80,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 140
            ),
            // FASHION (3개)
            Product(
                name = "러닝화",
                description = "가벼운 러닝화",
                price = 180000L,
                stock = 200,
                category = ProductCategory.FASHION,
                specifications = emptyMap(),
                salesCount = 130
            ),
            Product(
                name = "블랙 청바지",
                description = "슬림핏 청바지",
                price = 95000L,
                stock = 150,
                category = ProductCategory.FASHION,
                specifications = emptyMap(),
                salesCount = 100
            ),
            Product(
                name = "가죽 코트",
                description = "프리미엄 가죽",
                price = 380000L,
                stock = 30,
                category = ProductCategory.FASHION,
                specifications = emptyMap(),
                salesCount = 60
            ),
            // FOOD (2개)
            Product(
                name = "유기농 현미",
                description = "국내산 현미",
                price = 48000L,
                stock = 500,
                category = ProductCategory.FOOD,
                specifications = emptyMap(),
                salesCount = 270
            ),
            Product(
                name = "원두 커피 블렌드",
                description = "아라비카 원두",
                price = 28000L,
                stock = 300,
                category = ProductCategory.FOOD,
                specifications = emptyMap(),
                salesCount = 190
            ),
            // BOOKS (3개)
            Product(
                name = "Effective Java",
                description = "자바 프로그래밍 바이블",
                price = 38000L,
                stock = 100,
                category = ProductCategory.BOOKS,
                specifications = emptyMap(),
                salesCount = 85
            ),
            Product(
                name = "Refactoring",
                description = "리팩터링 2판",
                price = 35000L,
                stock = 80,
                category = ProductCategory.BOOKS,
                specifications = emptyMap(),
                salesCount = 160
            ),
            Product(
                name = "Domain-Driven Design",
                description = "DDD 핵심 가이드",
                price = 42000L,
                stock = 60,
                category = ProductCategory.BOOKS,
                specifications = emptyMap(),
                salesCount = 75
            ),
            // HOME (2개)
            Product(
                name = "공기청정기 Max",
                description = "대형 공간용",
                price = 320000L,
                stock = 60,
                category = ProductCategory.HOME,
                specifications = emptyMap(),
                salesCount = 55
            ),
            Product(
                name = "로봇 청소기",
                description = "자동 청소",
                price = 380000L,
                stock = 40,
                category = ProductCategory.HOME,
                specifications = emptyMap(),
                salesCount = 70
            ),
            // SPORTS (1개)
            Product(
                name = "프리미엄 요가 매트",
                description = "친환경 요가 매트",
                price = 52000L,
                stock = 150,
                category = ProductCategory.SPORTS,
                specifications = emptyMap(),
                salesCount = 110
            )
        )

        productsToCreate.forEach { product ->
            val saved = productJpaRepository.save(product)
            testProducts.add(saved.id!!)
        }
    }

    afterSpec {
        // 테스트 데이터 정리
        productJpaRepository.deleteAll()
        testProducts.clear()
    }

    describe("Product API E2E Tests") {

        describe("상품 목록 조회") {
            it("상품 목록을 조회할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.products.shouldNotBeEmpty()
                    result.products.size shouldBe 16 // 생성한 상품 수
                    result.pagination shouldNotBe null
                    result.pagination.totalElements shouldBe 16
                }
            }

            it("페이지네이션을 적용하여 상품 목록을 조회할 수 있어야 한다") {
                // When - 첫 페이지 조회
                val page0Response = restTemplate.getForEntity(
                    url("/products?page=0&size=5"),
                    ProductListResponse::class.java
                )

                // Then
                page0Response.statusCode shouldBe HttpStatus.OK
                page0Response.body?.let { result ->
                    result.products shouldHaveSize 5
                    result.pagination.currentPage shouldBe 0
                    result.pagination.size shouldBe 5
                    result.pagination.totalElements shouldBe 16
                    result.pagination.hasNext shouldBe true
                }

                // When - 두 번째 페이지 조회
                val page1Response = restTemplate.getForEntity(
                    url("/products?page=1&size=5"),
                    ProductListResponse::class.java
                )

                // Then
                page1Response.statusCode shouldBe HttpStatus.OK
                page1Response.body?.let { result ->
                    result.products shouldHaveSize 5
                    result.pagination.currentPage shouldBe 1
                    result.pagination.size shouldBe 5
                    result.pagination.hasPrevious shouldBe true
                }
            }

            it("카테고리로 상품을 필터링할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products?category=ELECTRONICS"),
                    ProductListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.products.shouldNotBeEmpty()
                    result.products shouldHaveSize 5 // ELECTRONICS 상품 5개
                    result.products.forEach { product ->
                        product.category shouldBe ProductCategory.ELECTRONICS
                    }
                }
            }

            it("정렬 옵션(newest)을 적용하여 상품 목록을 조회할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products?sort=newest"),
                    ProductListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.products.shouldNotBeEmpty()
            }

            it("정렬 옵션(price_low)을 적용하여 상품 목록을 조회할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products?sort=price_low"),
                    ProductListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.products.shouldNotBeEmpty()
                    // 가격 오름차순 정렬 확인
                    if (result.products.size > 1) {
                        for (i in 0 until result.products.size - 1) {
                            (result.products[i].price <= result.products[i + 1].price) shouldBe true
                        }
                    }
                }
            }

            it("정렬 옵션(price_high)을 적용하여 상품 목록을 조회할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products?sort=price_high"),
                    ProductListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.products.shouldNotBeEmpty()
                    // 가격 내림차순 정렬 확인
                    if (result.products.size > 1) {
                        for (i in 0 until result.products.size - 1) {
                            (result.products[i].price >= result.products[i + 1].price) shouldBe true
                        }
                    }
                }
            }

            it("카테고리와 정렬 옵션을 함께 적용할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products?category=BOOKS&sort=price_low&page=0&size=10"),
                    ProductListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.products.shouldNotBeEmpty()
                    result.products shouldHaveSize 3 // BOOKS 상품 3개
                    result.products.forEach { product ->
                        product.category shouldBe ProductCategory.BOOKS
                    }
                    // 가격 오름차순 확인
                    if (result.products.size > 1) {
                        for (i in 0 until result.products.size - 1) {
                            (result.products[i].price <= result.products[i + 1].price) shouldBe true
                        }
                    }
                }
            }
        }

        describe("인기 상품 조회") {
            it("최근 3일간 인기 상품을 조회할 수 있어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products/top?days=3&limit=5"),
                    TopProductsResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { result ->
                    result.period shouldNotBe null
                    result.period.days shouldBe 3
                    result.products.shouldNotBeEmpty()
                    result.products.size shouldBe 5

                    // 랭킹이 올바르게 설정되어 있는지 확인
                    result.products.forEachIndexed { index, product ->
                        product.rank shouldBe index + 1
                    }
                }
            }

            it("인기 상품은 판매량 순으로 정렬되어야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products/top?days=7&limit=10"),
                    TopProductsResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.products.shouldNotBeEmpty()

                    // 판매량이 내림차순으로 정렬되어 있는지 확인
                    val salesCounts = result.products.map { it.salesCount }
                    salesCounts shouldBe salesCounts.sortedDescending()

                    // 가장 많이 팔린 상품이 1등
                    result.products[0].name shouldBe "무선 이어폰 Pro"
                    result.products[0].salesCount shouldBe 320
                }
            }

            it("조회 기간을 커스텀하여 인기 상품을 조회할 수 있어야 한다") {
                // When - 1일
                val response1Day = restTemplate.getForEntity(
                    url("/products/top?days=1&limit=3"),
                    TopProductsResponse::class.java
                )

                // Then
                response1Day.statusCode shouldBe HttpStatus.OK
                response1Day.body?.period?.days shouldBe 1
                response1Day.body?.products?.size shouldBe 3

                // When - 7일
                val response7Days = restTemplate.getForEntity(
                    url("/products/top?days=7&limit=3"),
                    TopProductsResponse::class.java
                )

                // Then
                response7Days.statusCode shouldBe HttpStatus.OK
                response7Days.body?.period?.days shouldBe 7
            }
        }

        describe("상품 상세 조회") {
            it("상품 ID로 상품 상세 정보를 조회할 수 있어야 한다") {
                // Given - 생성한 첫 번째 상품 ID 사용
                val firstProductId = testProducts.first()

                // When
                val response = restTemplate.getForEntity(
                    url("/products/$firstProductId"),
                    ProductDetailResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { product ->
                    product.id shouldBe firstProductId
                    product.name shouldBe "노트북 Pro"
                    product.description shouldBe "고성능 노트북"
                    product.price shouldBe 1800000L
                    product.stock shouldBe 50
                    product.category shouldBe ProductCategory.ELECTRONICS
                    product.salesCount shouldBe 180
                    product.createdAt shouldNotBe null
                    product.updatedAt shouldNotBe null
                }
            }

            it("존재하지 않는 상품 ID로 조회 시 404를 반환해야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products/${UUID.randomUUID()}"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("상품 상세 정보에는 specifications 정보가 포함되어야 한다") {
                // Given
                val firstProductId = testProducts.first()

                // When
                val response = restTemplate.getForEntity(
                    url("/products/$firstProductId"),
                    ProductDetailResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.specifications shouldNotBe null
                response.body?.specifications?.get("cpu") shouldBe "Intel i9"
                response.body?.specifications?.get("ram") shouldBe "32GB"
            }
        }

        describe("상품 재고 조회") {
            it("상품 ID로 재고 정보를 조회할 수 있어야 한다") {
                // Given
                val firstProductId = testProducts.first()

                // When
                val response = restTemplate.getForEntity(
                    url("/products/$firstProductId/stock"),
                    ProductStockResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body shouldNotBe null
                response.body?.let { stock ->
                    stock.id shouldBe firstProductId
                    stock.productName shouldBe "노트북 Pro"
                    stock.stock shouldBe 50
                    stock.isAvailable shouldBe true
                    stock.lastUpdatedAt shouldNotBe null
                }
            }

            it("재고 정보에는 재고 가용성 여부가 포함되어야 한다") {
                // Given
                val firstProductId = testProducts.first()

                // When
                val response = restTemplate.getForEntity(
                    url("/products/$firstProductId/stock"),
                    ProductStockResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { stock ->
                    if (stock.stock > 0) {
                        stock.isAvailable shouldBe true
                    } else {
                        stock.isAvailable shouldBe false
                    }
                }
            }

            it("존재하지 않는 상품의 재고 조회 시 404를 반환해야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products/${UUID.randomUUID()}/stock"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("복합 사용 시나리오") {
            it("상품 목록 조회 후 특정 상품의 상세 정보와 재고를 순차적으로 조회할 수 있어야 한다") {
                // Given - 상품 목록 조회
                val listResponse = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)
                listResponse.statusCode shouldBe HttpStatus.OK
                val productId = listResponse.body?.products?.first()?.id

                // When - 상품 상세 조회
                val detailResponse = restTemplate.getForEntity(
                    url("/products/$productId"),
                    ProductDetailResponse::class.java
                )

                // Then
                detailResponse.statusCode shouldBe HttpStatus.OK
                detailResponse.body?.id shouldBe productId

                // When - 재고 조회
                val stockResponse = restTemplate.getForEntity(
                    url("/products/$productId/stock"),
                    ProductStockResponse::class.java
                )

                // Then
                stockResponse.statusCode shouldBe HttpStatus.OK
                stockResponse.body?.id shouldBe productId
                stockResponse.body?.stock shouldBe detailResponse.body?.stock
            }

            it("여러 카테고리의 상품을 순차적으로 조회할 수 있어야 한다") {
                // When - ELECTRONICS 카테고리 조회
                val electronicsResponse = restTemplate.getForEntity(
                    url("/products?category=ELECTRONICS"),
                    ProductListResponse::class.java
                )

                // Then
                electronicsResponse.statusCode shouldBe HttpStatus.OK
                electronicsResponse.body?.products?.forEach { product ->
                    product.category shouldBe ProductCategory.ELECTRONICS
                }
                electronicsResponse.body?.products?.shouldHaveSize(5)

                // When - BOOKS 카테고리 조회
                val booksResponse = restTemplate.getForEntity(
                    url("/products?category=BOOKS"),
                    ProductListResponse::class.java
                )

                // Then
                booksResponse.statusCode shouldBe HttpStatus.OK
                booksResponse.body?.products?.forEach { product ->
                    product.category shouldBe ProductCategory.BOOKS
                }
                booksResponse.body?.products?.shouldHaveSize(3)
            }

            it("인기 상품 조회 후 해당 상품들의 상세 정보를 확인할 수 있어야 한다") {
                // Given - 인기 상품 조회
                val topProductsResponse = restTemplate.getForEntity(
                    url("/products/top?days=7&limit=3"),
                    TopProductsResponse::class.java
                )

                topProductsResponse.statusCode shouldBe HttpStatus.OK
                val topProducts = topProductsResponse.body?.products

                // When & Then - 각 인기 상품의 상세 정보 조회
                topProducts?.forEach { topProduct ->
                    val detailResponse = restTemplate.getForEntity(
                        url("/products/${topProduct.id}"),
                        ProductDetailResponse::class.java
                    )

                    detailResponse.statusCode shouldBe HttpStatus.OK
                    detailResponse.body?.let { detail ->
                        detail.id shouldBe topProduct.id
                        detail.name shouldBe topProduct.name
                        detail.price shouldBe topProduct.price
                        detail.salesCount shouldBe topProduct.salesCount
                    }
                }
            }

            it("카테고리 + 가격순 정렬 + 페이지네이션 조합 시나리오") {
                // When - ELECTRONICS 카테고리, 가격순, 첫 페이지
                val response = restTemplate.getForEntity(
                    url("/products?category=ELECTRONICS&sort=price_low&page=0&size=3"),
                    ProductListResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.let { result ->
                    result.products shouldHaveSize 3
                    result.products.all { it.category == ProductCategory.ELECTRONICS.name } shouldBe true
                    result.pagination.currentPage shouldBe 0
                    result.pagination.size shouldBe 3
                    result.pagination.totalElements shouldBe 5 // ELECTRONICS 총 5개

                    // 가격 오름차순 확인
                    for (i in 0 until result.products.size - 1) {
                        (result.products[i].price <= result.products[i + 1].price) shouldBe true
                    }
                }
            }
        }
    }
})
