package com.hhplus.ecommerce.infrastructure.product

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
class ProductRepositoryIntegrationTest(
    private val productJpaRepository: ProductJpaRepository
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val testProducts = mutableListOf<UUID>()

    init {
        beforeSpec {
            // 테스트용 상품 생성 - 각 카테고리별로 충분한 데이터 생성
            val productsToCreate = listOf(
                // ELECTRONICS (3개)
                Product(
                    name = "노트북 ABC",
                    description = "고성능 노트북",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 150
                ),
                Product(
                    name = "스마트폰 XYZ",
                    description = "최신 스마트폰",
                    price = 1200000L,
                    stock = 100,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 200
                ),
                Product(
                    name = "태블릿",
                    description = "태블릿 PC",
                    price = 800000L,
                    stock = 30,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 80
                ),
                // FASHION (2개)
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
                    name = "Kotlin 가이드",
                    description = "Kotlin 프로그래밍",
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
                // SPORTS (2개)
                Product(
                    name = "요가 매트",
                    description = "두꺼운 요가 매트",
                    price = 45000L,
                    stock = 150,
                    category = ProductCategory.SPORTS,
                    specifications = emptyMap(),
                    salesCount = 100
                ),
                Product(
                    name = "덤벨 세트",
                    description = "가변 덤벨",
                    price = 120000L,
                    stock = 80,
                    category = ProductCategory.SPORTS,
                    specifications = emptyMap(),
                    salesCount = 65
                ),
                // BEAUTY (2개)
                Product(
                    name = "스킨케어 세트",
                    description = "보습 스킨케어",
                    price = 89000L,
                    stock = 200,
                    category = ProductCategory.BEAUTY,
                    specifications = emptyMap(),
                    salesCount = 110
                ),
                Product(
                    name = "향수",
                    description = "프리미엄 향수",
                    price = 150000L,
                    stock = 50,
                    category = ProductCategory.BEAUTY,
                    specifications = emptyMap(),
                    salesCount = 75
                )
            )

            productsToCreate.forEach { product ->
                val saved = productJpaRepository.save(product)
                testProducts.add(saved.id!!)
            }
        }

        afterSpec {
            productJpaRepository.deleteAll()
            testProducts.clear()
        }

    describe("ProductRepository 통합 테스트 - findById") {
        context("정상 케이스") {
            it("존재하는 상품 ID로 상품을 조회한다") {
                // given
                val productId = testProducts.first()

                // when
                val product = productJpaRepository.findById(productId)

                // then
                product.isPresent shouldBe true
                product.get().id shouldBe productId
                product.get().name shouldBe "노트북 ABC"
                product.get().price shouldBe 1500000L
                product.get().stock shouldBe 50
                product.get().category shouldBe ProductCategory.ELECTRONICS
                product.get().salesCount shouldBe 150
            }

            it("다양한 카테고리의 상품을 ID로 조회할 수 있다") {
                // when
                val electronics = productJpaRepository.findById(testProducts[0]) // ELECTRONICS
                val fashion = productJpaRepository.findById(testProducts[3])      // FASHION

                // then
                electronics.get().category shouldBe ProductCategory.ELECTRONICS
                fashion.get().category shouldBe ProductCategory.FASHION
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 상품 ID로 조회 시 빈 Optional을 반환한다") {
                // given
                val invalidProductId = UUID.randomUUID()

                // when
                val product = productJpaRepository.findById(invalidProductId)

                // then
                product.isPresent shouldBe false
            }
        }
    }

    describe("ProductRepository 통합 테스트 - findAll") {
        context("정상 케이스") {
            it("모든 상품을 조회한다") {
                // when
                val products = productJpaRepository.findAll()

                // then
                products.size shouldBe 15 // 생성한 상품 수
                products.all { it.id != null } shouldBe true
                products.all { it.price > 0 } shouldBe true
            }

            it("조회된 상품들이 다양한 카테고리를 포함한다") {
                // when
                val products = productJpaRepository.findAll()

                // then
                val categories = products.map { it.category }.toSet()
                categories.contains(ProductCategory.ELECTRONICS) shouldBe true
                categories.contains(ProductCategory.FASHION) shouldBe true
                categories.contains(ProductCategory.FOOD) shouldBe true
                categories.contains(ProductCategory.BOOKS) shouldBe true
                categories.contains(ProductCategory.HOME) shouldBe true
                categories.contains(ProductCategory.SPORTS) shouldBe true
                categories.contains(ProductCategory.BEAUTY) shouldBe true
            }

            it("조회된 각 상품이 필수 정보를 모두 포함한다") {
                // when
                val products = productJpaRepository.findAll()

                // then
                products.forEach { product ->
                    product.id shouldNotBe null
                    product.name shouldNotBe ""
                    product.description shouldNotBe ""
                    product.price shouldNotBe 0L
                    product.stock shouldNotBe null
                    product.category shouldNotBe null
                    product.createdAt shouldNotBe ""
                    product.updatedAt shouldNotBe ""
                }
            }
        }
    }

    describe("ProductJpaRepository 통합 테스트 - findAllWithFilter") {
        context("카테고리 필터링") {
            it("ELECTRONICS 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.ELECTRONICS, pageable)

                // then
                page.content.size shouldBe 3
                page.content.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
                page.content.map { it.id } shouldContainExactlyInAnyOrder listOf(testProducts[0], testProducts[1], testProducts[2])
                page.totalElements shouldBe 3
            }

            it("FASHION 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.FASHION, pageable)

                // then
                page.content.size shouldBe 2
                page.content.all { it.category == ProductCategory.FASHION } shouldBe true
                page.totalElements shouldBe 2
            }

            it("FOOD 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.FOOD, pageable)

                // then
                page.content.size shouldBe 2
                page.content.all { it.category == ProductCategory.FOOD } shouldBe true
                page.totalElements shouldBe 2
            }

            it("BOOKS 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.BOOKS, pageable)

                // then
                page.content.size shouldBe 2
                page.content.all { it.category == ProductCategory.BOOKS } shouldBe true
                page.totalElements shouldBe 2
            }

            it("HOME 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.HOME, pageable)

                // then
                page.content.size shouldBe 2
                page.content.all { it.category == ProductCategory.HOME } shouldBe true
                page.totalElements shouldBe 2
            }

            it("SPORTS 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.SPORTS, pageable)

                // then
                page.content.size shouldBe 2
                page.content.all { it.category == ProductCategory.SPORTS } shouldBe true
                page.totalElements shouldBe 2
            }

            it("BEAUTY 카테고리의 상품만 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.BEAUTY, pageable)

                // then
                page.content.size shouldBe 2
                page.content.all { it.category == ProductCategory.BEAUTY } shouldBe true
                page.totalElements shouldBe 2
            }

            it("category가 null이면 모든 상품을 조회한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 20)

                // when
                val page = productJpaRepository.findAllWithFilter(null, pageable)

                // then
                page.content.size shouldBe 15
                page.totalElements shouldBe 15

                // 모든 카테고리가 포함되어야 함
                val categories = page.content.map { it.category }.toSet()
                categories.contains(ProductCategory.ELECTRONICS) shouldBe true
                categories.contains(ProductCategory.FASHION) shouldBe true
                categories.contains(ProductCategory.FOOD) shouldBe true
                categories.contains(ProductCategory.BOOKS) shouldBe true
                categories.contains(ProductCategory.HOME) shouldBe true
                categories.contains(ProductCategory.SPORTS) shouldBe true
                categories.contains(ProductCategory.BEAUTY) shouldBe true
            }
        }

        context("페이징") {
            it("페이지 크기에 맞게 상품을 조회한다") {
                // given - 첫 페이지, 크기 5
                val pageable = org.springframework.data.domain.PageRequest.of(0, 5)

                // when
                val page = productJpaRepository.findAllWithFilter(null, pageable)

                // then
                page.content.size shouldBe 5
                page.totalElements shouldBe 15
                page.totalPages shouldBe 3
                page.isFirst shouldBe true
                page.isLast shouldBe false
            }

            it("두 번째 페이지를 조회한다") {
                // given - 두 번째 페이지, 크기 5
                val pageable = org.springframework.data.domain.PageRequest.of(1, 5)

                // when
                val page = productJpaRepository.findAllWithFilter(null, pageable)

                // then
                page.content.size shouldBe 5
                page.number shouldBe 1 // 페이지 번호
                page.totalElements shouldBe 15
                page.isFirst shouldBe false
                page.isLast shouldBe false
            }

            it("마지막 페이지를 조회한다") {
                // given - 세 번째 페이지, 크기 5
                val pageable = org.springframework.data.domain.PageRequest.of(2, 5)

                // when
                val page = productJpaRepository.findAllWithFilter(null, pageable)

                // then
                page.content.size shouldBe 5
                page.number shouldBe 2
                page.isFirst shouldBe false
                page.isLast shouldBe true
            }

            it("카테고리 필터와 페이징을 함께 사용한다") {
                // given - ELECTRONICS 카테고리, 페이지 크기 2
                val pageable = org.springframework.data.domain.PageRequest.of(0, 2)

                // when
                val page = productJpaRepository.findAllWithFilter(ProductCategory.ELECTRONICS, pageable)

                // then
                page.content.size shouldBe 2
                page.totalElements shouldBe 3 // ELECTRONICS 총 3개
                page.totalPages shouldBe 2 // 2개씩 나누면 2페이지
                page.content.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 카테고리로 조회하면 빈 페이지를 반환한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

                // 모든 상품을 삭제하고 다른 카테고리만 남김
                productJpaRepository.deleteAll()
                productJpaRepository.save(Product(
                    name = "테스트 상품",
                    description = "테스트",
                    price = 10000L,
                    stock = 10,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 0
                ))

                // when - FASHION 카테고리 조회 (존재하지 않음)
                val page = productJpaRepository.findAllWithFilter(ProductCategory.FASHION, pageable)

                // then
                page.content.size shouldBe 0
                page.totalElements shouldBe 0
                page.isEmpty shouldBe true
            }

            it("범위를 벗어난 페이지를 조회하면 빈 페이지를 반환한다") {
                // given - 존재하지 않는 페이지 번호
                val pageable = org.springframework.data.domain.PageRequest.of(100, 10)

                // when
                val page = productJpaRepository.findAllWithFilter(null, pageable)

                // then
                page.content.size shouldBe 0
                page.number shouldBe 100
            }
        }

        context("데이터 정합성") {
            it("카테고리별 조회 결과의 합이 전체 조회 결과와 일치한다") {
                // given
                val pageable = org.springframework.data.domain.PageRequest.of(0, 100)

                // when
                val allProducts = productJpaRepository.findAllWithFilter(null, pageable)
                val electronics = productJpaRepository.findAllWithFilter(ProductCategory.ELECTRONICS, pageable)
                val fashion = productJpaRepository.findAllWithFilter(ProductCategory.FASHION, pageable)
                val food = productJpaRepository.findAllWithFilter(ProductCategory.FOOD, pageable)
                val books = productJpaRepository.findAllWithFilter(ProductCategory.BOOKS, pageable)
                val home = productJpaRepository.findAllWithFilter(ProductCategory.HOME, pageable)
                val sports = productJpaRepository.findAllWithFilter(ProductCategory.SPORTS, pageable)
                val beauty = productJpaRepository.findAllWithFilter(ProductCategory.BEAUTY, pageable)

                // then
                val totalByCategoryCount = electronics.totalElements + fashion.totalElements + food.totalElements +
                        books.totalElements + home.totalElements + sports.totalElements + beauty.totalElements
                totalByCategoryCount shouldBe allProducts.totalElements
                totalByCategoryCount shouldBe 15
            }
        }
    }

    describe("ProductRepository 통합 테스트 - save") {
        context("정상 케이스 - 신규 상품 저장") {
            it("새로운 상품을 저장한다") {
                // given
                val newProduct = Product(
                    name = "신규 상품",
                    description = "새로 추가된 상품입니다",
                    price = 50000L,
                    stock = 30,
                    category = ProductCategory.ELECTRONICS,
                    specifications = mapOf("type" to "new"),
                    salesCount = 0
                )

                // when
                val saved = productJpaRepository.save(newProduct)

                // then
                saved.id shouldNotBe null
                saved.name shouldBe "신규 상품"

                // 저장 후 조회 가능
                val found = productJpaRepository.findById(saved.id!!)
                found.isPresent shouldBe true
                found.get().name shouldBe "신규 상품"
            }
        }

        context("정상 케이스 - 기존 상품 업데이트") {
            it("기존 상품의 재고를 업데이트한다") {
                // given
                val productId = testProducts.first()
                val originalProduct = productJpaRepository.findById(productId).get()
                val originalStock = originalProduct.stock

                // when - 재고 차감
                originalProduct.stock = originalStock - 5
                productJpaRepository.save(originalProduct)

                // then
                val updated = productJpaRepository.findById(productId).get()
                updated.stock shouldBe (originalStock - 5)
            }

            it("기존 상품의 판매량을 업데이트한다") {
                // given
                val productId = testProducts[1]
                val originalProduct = productJpaRepository.findById(productId).get()
                val originalSalesCount = originalProduct.salesCount

                // when - 판매량 증가
                originalProduct.salesCount = originalSalesCount + 10
                productJpaRepository.save(originalProduct)

                // then
                val updated = productJpaRepository.findById(productId).get()
                updated.salesCount shouldBe (originalSalesCount + 10)
            }

            it("주문 시나리오: 재고 차감과 판매량 증가를 동시에 업데이트한다") {
                // given
                val productId = testProducts[2]
                val originalProduct = productJpaRepository.findById(productId).get()
                val originalStock = originalProduct.stock
                val originalSalesCount = originalProduct.salesCount
                val orderQuantity = 5

                // when - 주문 처리
                originalProduct.stock = originalStock - orderQuantity
                originalProduct.salesCount = originalSalesCount + orderQuantity
                productJpaRepository.save(originalProduct)

                // then
                val updated = productJpaRepository.findById(productId).get()
                updated.stock shouldBe (originalStock - orderQuantity)
                updated.salesCount shouldBe (originalSalesCount + orderQuantity)
            }

            it("재고가 0이 되도록 업데이트할 수 있다") {
                // given
                val productId = testProducts[3]
                val originalProduct = productJpaRepository.findById(productId).get()

                // when - 재고를 0으로 설정
                originalProduct.stock = 0
                productJpaRepository.save(originalProduct)

                // then
                val updated = productJpaRepository.findById(productId).get()
                updated.stock shouldBe 0
            }
        }

        context("데이터 무결성") {
            it("save 후에도 다른 필드는 변경되지 않는다") {
                // given
                val productId = testProducts[4]
                val originalProduct = productJpaRepository.findById(productId).get()
                val originalName = originalProduct.name
                val originalPrice = originalProduct.price
                val originalCategory = originalProduct.category

                // when - stock만 변경
                originalProduct.stock = originalProduct.stock - 1
                productJpaRepository.save(originalProduct)

                // then - 다른 필드는 유지
                val updated = productJpaRepository.findById(productId).get()
                updated.name shouldBe originalName
                updated.price shouldBe originalPrice
                updated.category shouldBe originalCategory
            }

            it("여러 번 save를 호출해도 정상 동작한다") {
                // given
                val productId = testProducts[5]
                val originalProduct = productJpaRepository.findById(productId).get()

                // when - 여러 번 업데이트
                originalProduct.stock = 100
                productJpaRepository.save(originalProduct)

                originalProduct.stock = 90
                productJpaRepository.save(originalProduct)

                originalProduct.stock = 80
                productJpaRepository.save(originalProduct)

                // then
                val updated = productJpaRepository.findById(productId).get()
                updated.stock shouldBe 80
            }
        }
    }

    describe("ProductRepository 통합 테스트 - 복합 시나리오") {
        context("실제 비즈니스 흐름") {
            it("상품 조회 -> 재고 확인 -> 재고 차감 -> 판매량 증가 플로우") {
                // 상품 조회
                val productId = testProducts[0]
                val product = productJpaRepository.findById(productId)
                product.isPresent shouldBe true

                // 재고 확인
                val originalStock = product.get().stock
                val orderQuantity = 3
                originalStock shouldNotBe 0 // 재고가 있어야 함

                // 재고 차감
                product.get().stock = originalStock - orderQuantity

                // 판매량 증가
                val originalSalesCount = product.get().salesCount
                product.get().salesCount = originalSalesCount + orderQuantity

                // 저장
                productJpaRepository.save(product.get())

                // 검증
                val updated = productJpaRepository.findById(productId).get()
                updated.stock shouldBe (originalStock - orderQuantity)
                updated.salesCount shouldBe (originalSalesCount + orderQuantity)
            }

            it("카테고리별 상품 조회 -> 특정 상품 업데이트") {
                // 1. 카테고리로 상품 조회
                val beautyProducts = productJpaRepository.findAll()
                    .filter { it.category == ProductCategory.BEAUTY }
                beautyProducts shouldHaveSize 2

                // 2. 첫 번째 상품 선택
                val product = beautyProducts[0]
                val productId = product.id

                // 3. 재고 업데이트
                val originalStock = product.stock
                product.stock = product.stock - 5
                productJpaRepository.save(product)

                // 4. 다시 조회하여 변경 확인
                val updated = productJpaRepository.findById(productId!!).get()
                updated.stock shouldBe (originalStock - 5)
            }

            it("전체 상품 중 재고가 있는 상품만 필터링") {
                // when
                val allProducts = productJpaRepository.findAll()
                val availableProducts = allProducts.filter { it.stock > 0 }

                // then
                availableProducts.size shouldBe 15 // 모든 상품이 재고가 있음
                availableProducts.all { it.stock > 0 } shouldBe true
            }

            it("인기 상품 TOP 5 조회 시나리오") {
                // when
                val allProducts = productJpaRepository.findAll()
                val topProducts = allProducts
                    .filter { it.salesCount > 0 }
                    .sortedByDescending { it.salesCount }
                    .take(5)

                // then
                topProducts.size shouldBe 5
                topProducts.all { it.salesCount > 0 } shouldBe true
                // 판매량 내림차순 확인
                for (i in 0 until topProducts.size - 1) {
                    topProducts[i].salesCount shouldNotBe null
                    (topProducts[i].salesCount >= topProducts[i + 1].salesCount) shouldBe true
                }
            }
        }
    }
    }
}
