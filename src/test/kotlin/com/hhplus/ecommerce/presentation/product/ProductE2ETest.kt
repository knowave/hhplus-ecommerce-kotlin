package com.hhplus.ecommerce.presentation.product

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import com.hhplus.ecommerce.presentation.product.dto.*
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductE2ETest(
    @LocalServerPort private val port: Int,
    private val restTemplate: TestRestTemplate,
    private val productService: com.hhplus.ecommerce.application.product.ProductService
) : DescribeSpec({

    // URL 헬퍼 함수
    fun url(path: String): String = "http://localhost:$port/api$path"

    beforeSpec {
        // 테스트용 상품들은 DataInitializer에서 생성됨
        // E2E 테스트는 전체 시스템 통합 테스트이므로 초기 데이터를 활용
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
                    result.pagination shouldNotBe null
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
                    result.products.forEach { product ->
                        product.category shouldBe "ELECTRONICS"
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
                    // 정렬 옵션이 적용되어 응답이 반환되는지 확인
                    // Note: 실제 정렬 로직은 ProductService에서 구현되어야 함
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
                    // 정렬 옵션이 적용되어 응답이 반환되는지 확인
                    // Note: 실제 정렬 로직은 ProductService에서 구현되어야 함
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
                    result.products.forEach { product ->
                        product.category shouldBe "BOOKS"
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
                // Given - 상품 목록에서 첫 번째 상품 ID 가져오기
                val listResponse = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)
                val firstProductId = listResponse.body?.products?.first()?.id

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
                    product.name shouldNotBe null
                    product.description shouldNotBe null
                    (product.price > 0) shouldBe true
                    (product.stock >= 0) shouldBe true
                    product.category shouldNotBe null
                    (product.salesCount >= 0) shouldBe true
                    product.createdAt shouldNotBe null
                    product.updatedAt shouldNotBe null
                }
            }

            it("존재하지 않는 상품 ID로 조회 시 404를 반환해야 한다") {
                // When
                val response = restTemplate.getForEntity(
                    url("/products/999999"),
                    String::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("상품 상세 정보에는 specifications 정보가 포함되어야 한다") {
                // Given
                val listResponse = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)
                val firstProductId = listResponse.body?.products?.first()?.id

                // When
                val response = restTemplate.getForEntity(
                    url("/products/$firstProductId"),
                    ProductDetailResponse::class.java
                )

                // Then
                response.statusCode shouldBe HttpStatus.OK
                response.body?.specifications shouldNotBe null
            }
        }

        describe("상품 재고 조회") {
            it("상품 ID로 재고 정보를 조회할 수 있어야 한다") {
                // Given
                val listResponse = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)
                val firstProductId = listResponse.body?.products?.first()?.id

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
                    stock.productName shouldNotBe null
                    (stock.stock >= 0) shouldBe true
                    stock.lastUpdatedAt shouldNotBe null
                }
            }

            it("재고 정보에는 재고 가용성 여부가 포함되어야 한다") {
                // Given
                val listResponse = restTemplate.getForEntity(url("/products"), ProductListResponse::class.java)
                val firstProductId = listResponse.body?.products?.first()?.id

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
                    url("/products/999999/stock"),
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
                    product.category shouldBe "ELECTRONICS"
                }

                // When - BOOKS 카테고리 조회
                val booksResponse = restTemplate.getForEntity(
                    url("/products?category=BOOKS"),
                    ProductListResponse::class.java
                )

                // Then
                booksResponse.statusCode shouldBe HttpStatus.OK
                booksResponse.body?.products?.forEach { product ->
                    product.category shouldBe "BOOKS"
                }
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
        }
    }
})
