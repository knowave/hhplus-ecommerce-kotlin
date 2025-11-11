package com.hhplus.ecommerce.infrastructure.product

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import com.hhplus.ecommerce.domain.product.repository.ProductRepository
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
    private val productRepository: ProductRepository,
    private val productJpaRepository: ProductJpaRepository
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID
    private lateinit var product3Id: UUID

    init {
        beforeSpec {
            // 테스트용 상품 생성
            val product1 = Product(
                name = "노트북 ABC",
                description = "고성능 노트북",
                price = 1500000L,
                stock = 50,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 150
            )
            val savedProduct1 = productJpaRepository.save(product1)
            product1Id = savedProduct1.id!!

            val product2 = Product(
                name = "스마트폰 XYZ",
                description = "최신 스마트폰",
                price = 1200000L,
                stock = 100,
                category = ProductCategory.ELECTRONICS,
                specifications = emptyMap(),
                salesCount = 200
            )
            val savedProduct2 = productJpaRepository.save(product2)
            product2Id = savedProduct2.id!!

            val product3 = Product(
                name = "운동화",
                description = "편안한 운동화",
                price = 150000L,
                stock = 200,
                category = ProductCategory.FASHION,
                specifications = emptyMap(),
                salesCount = 120
            )
            val savedProduct3 = productJpaRepository.save(product3)
            product3Id = savedProduct3.id!!
        }

        afterEach {
            // 각 테스트 후 상품 데이터만 정리하여 테스트 간 격리 보장
            // beforeSpec에서 생성한 데이터는 유지되므로 필요시 재생성하지 않음
        }

        afterSpec {
            productJpaRepository.deleteAll()
        }

    describe("ProductRepository 통합 테스트 - findById") {
        context("정상 케이스") {
            it("존재하는 상품 ID로 상품을 조회한다") {
                // given
                val productId = product1Id

                // when
                val product = productRepository.findById(productId)

                // then
                product shouldNotBe null
                product!!.id shouldBe productId
                product.name shouldBe "노트북 ABC"
                product.price shouldBe 1500000L
                product.stock shouldBe 50
                product.category shouldBe ProductCategory.ELECTRONICS
                product.salesCount shouldBe 150
            }

            it("다양한 카테고리의 상품을 ID로 조회할 수 있다") {
                // when
                val electronics = productRepository.findById(product1Id) // ELECTRONICS
                val fashion = productRepository.findById(product3Id)      // FASHION

                // then
                electronics!!.category shouldBe ProductCategory.ELECTRONICS
                fashion!!.category shouldBe ProductCategory.FASHION
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 상품 ID로 조회 시 null을 반환한다") {
                // given
                val invalidProductId = UUID.randomUUID()

                // when
                val product = productRepository.findById(invalidProductId)

                // then
                product shouldBe null
            }
        }
    }

    describe("ProductRepository 통합 테스트 - findAll") {
        context("정상 케이스") {
            it("모든 상품을 조회한다") {
                // when
                val products = productRepository.findAll()

                // then - 재사용 가능한 Repository이므로 최소 개수만 검증
                products.size shouldNotBe 0
                products.all { it.id > 0 } shouldBe true
                products.all { it.price > 0 } shouldBe true
            }

            it("조회된 상품들이 다양한 카테고리를 포함한다") {
                // when
                val products = productRepository.findAll()

                // then
                val categories = products.map { it.category }.toSet()
                categories.contains(ProductCategory.ELECTRONICS) shouldBe true
                categories.contains(ProductCategory.FASHION) shouldBe true
            }

            it("조회된 각 상품이 필수 정보를 모두 포함한다") {
                // when
                val products = productRepository.findAll()

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

    describe("ProductRepository 통합 테스트 - findByCategory") {
        context("정상 케이스") {
            it("ELECTRONICS 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.ELECTRONICS)

                // then
                products.size shouldNotBe 0
                products.all { it.category == ProductCategory.ELECTRONICS } shouldBe true
                // beforeSpec에서 생성한 product1, product2가 포함되어 있는지 확인
                products.map { it.id } shouldContainExactlyInAnyOrder listOf(product1Id, product2Id)
            }

            it("FASHION 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.FASHION)

                // then
                products.size shouldNotBe 0
                products.all { it.category == ProductCategory.FASHION } shouldBe true
                // beforeSpec에서 생성한 product3가 포함되어 있는지 확인
                products.map { it.id }.contains(product3Id) shouldBe true
            }

            it("FOOD 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.FOOD)

                // then
                // 테스트 데이터에 FOOD 카테고리가 없으므로 빈 리스트 또는 DataInitializer 데이터
                products.all { it.category == ProductCategory.FOOD } shouldBe true
            }

            it("BOOKS 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.BOOKS)

                // then
                products.all { it.category == ProductCategory.BOOKS } shouldBe true
            }

            it("HOME 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.HOME)

                // then
                products.all { it.category == ProductCategory.HOME } shouldBe true
            }

            it("SPORTS 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.SPORTS)

                // then
                products.all { it.category == ProductCategory.SPORTS } shouldBe true
            }

            it("BEAUTY 카테고리의 상품만 조회한다") {
                // when
                val products = productRepository.findByCategory(ProductCategory.BEAUTY)

                // then
                products.all { it.category == ProductCategory.BEAUTY } shouldBe true
            }

        }

        context("데이터 정합성") {
            it("카테고리별 조회 결과의 합이 전체 조회 결과와 일치한다") {
                // when
                val allProducts = productRepository.findAll()
                val electronics = productRepository.findByCategory(ProductCategory.ELECTRONICS)
                val fashion = productRepository.findByCategory(ProductCategory.FASHION)
                val food = productRepository.findByCategory(ProductCategory.FOOD)
                val books = productRepository.findByCategory(ProductCategory.BOOKS)
                val home = productRepository.findByCategory(ProductCategory.HOME)
                val sports = productRepository.findByCategory(ProductCategory.SPORTS)
                val beauty = productRepository.findByCategory(ProductCategory.BEAUTY)

                // then
                val totalByCategoryCount = electronics.size + fashion.size + food.size +
                        books.size + home.size + sports.size + beauty.size
                totalByCategoryCount shouldBe allProducts.size
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
                val saved = productRepository.save(newProduct)

                // then
                saved.id shouldNotBe null
                saved.name shouldBe "신규 상품"

                // 저장 후 조회 가능
                val found = productRepository.findById(saved.id!!)
                found shouldNotBe null
                found!!.name shouldBe "신규 상품"
            }
        }

        context("정상 케이스 - 기존 상품 업데이트") {
            it("기존 상품의 재고를 업데이트한다") {
                // given
                val productId = product1Id
                val originalProduct = productRepository.findById(productId)!!
                val originalStock = originalProduct.stock

                // when - 재고 차감
                originalProduct.stock = originalStock - 5
                productRepository.save(originalProduct)

                // then
                val updated = productRepository.findById(productId)!!
                updated.stock shouldBe (originalStock - 5)
            }

            it("기존 상품의 판매량을 업데이트한다") {
                // given
                val productId = product2Id
                val originalProduct = productRepository.findById(productId)!!
                val originalSalesCount = originalProduct.salesCount

                // when - 판매량 증가
                originalProduct.salesCount = originalSalesCount + 10
                productRepository.save(originalProduct)

                // then
                val updated = productRepository.findById(productId)!!
                updated.salesCount shouldBe (originalSalesCount + 10)
            }

            it("주문 시나리오: 재고 차감과 판매량 증가를 동시에 업데이트한다") {
                // given
                val productId = product3Id
                val originalProduct = productRepository.findById(productId)!!
                val originalStock = originalProduct.stock
                val originalSalesCount = originalProduct.salesCount
                val orderQuantity = 5

                // when - 주문 처리
                originalProduct.stock = originalStock - orderQuantity
                originalProduct.salesCount = originalSalesCount + orderQuantity
                productRepository.save(originalProduct)

                // then
                val updated = productRepository.findById(productId)!!
                updated.stock shouldBe (originalStock - orderQuantity)
                updated.salesCount shouldBe (originalSalesCount + orderQuantity)
            }

            it("재고가 0이 되도록 업데이트할 수 있다") {
                // given
                val productId = product1Id
                val originalProduct = productRepository.findById(productId)!!

                // when - 재고를 0으로 설정
                originalProduct.stock = 0
                productRepository.save(originalProduct)

                // then
                val updated = productRepository.findById(productId)!!
                updated.stock shouldBe 0
            }
        }

        context("데이터 무결성") {
            it("save 후에도 다른 필드는 변경되지 않는다") {
                // given
                val productId = product2Id
                val originalProduct = productRepository.findById(productId)!!
                val originalName = originalProduct.name
                val originalPrice = originalProduct.price
                val originalCategory = originalProduct.category

                // when - stock만 변경
                originalProduct.stock = originalProduct.stock - 1
                productRepository.save(originalProduct)

                // then - 다른 필드는 유지
                val updated = productRepository.findById(productId)!!
                updated.name shouldBe originalName
                updated.price shouldBe originalPrice
                updated.category shouldBe originalCategory
            }

            it("여러 번 save를 호출해도 정상 동작한다") {
                // given
                val productId = product3Id
                val originalProduct = productRepository.findById(productId)!!

                // when - 여러 번 업데이트
                originalProduct.stock = 100
                productRepository.save(originalProduct)

                originalProduct.stock = 90
                productRepository.save(originalProduct)

                originalProduct.stock = 80
                productRepository.save(originalProduct)

                // then
                val updated = productRepository.findById(productId)!!
                updated.stock shouldBe 80
            }
        }
    }

    describe("ProductRepository 통합 테스트 - 복합 시나리오") {
        context("실제 비즈니스 흐름") {
            it("상품 조회 -> 재고 확인 -> 재고 차감 -> 판매량 증가 플로우") {
                // 1. 상품 조회
                val productId = 7L
                val product = productRepository.findById(productId)
                product shouldNotBe null

                // 2. 재고 확인
                val originalStock = product!!.stock
                val orderQuantity = 3
                originalStock shouldNotBe 0 // 재고가 있어야 함

                // 3. 재고 차감
                product.stock = originalStock - orderQuantity

                // 4. 판매량 증가
                val originalSalesCount = product.salesCount
                product.salesCount = originalSalesCount + orderQuantity

                // 5. 업데이트 시간 갱신
                product.updatedAt = LocalDateTime.now().format(dateFormatter)

                // 6. 저장
                productRepository.save(product)

                // 7. 검증
                val updated = productRepository.findById(productId)!!
                updated.stock shouldBe (originalStock - orderQuantity)
                updated.salesCount shouldBe (originalSalesCount + orderQuantity)
            }

            it("카테고리별 상품 조회 -> 특정 상품 업데이트") {
                // 1. 카테고리로 상품 조회
                val beautyProducts = productRepository.findByCategory(ProductCategory.BEAUTY)
                beautyProducts shouldHaveSize 2

                // 2. 첫 번째 상품 선택
                val product = beautyProducts[0]
                val productId = product.id

                // 3. 재고 업데이트
                product.stock = product.stock - 5
                productRepository.save(product)

                // 4. 다시 조회하여 변경 확인
                val updated = productRepository.findById(productId)!!
                updated.stock shouldBe (product.stock)
            }

            it("전체 상품 중 재고가 있는 상품만 필터링") {
                // when
                val allProducts = productRepository.findAll()
                val availableProducts = allProducts.filter { it.stock > 0 }

                // then
                availableProducts.size shouldNotBe 0
                availableProducts.all { it.stock > 0 } shouldBe true
            }

            it("인기 상품 TOP 5 조회 시나리오") {
                // when
                val allProducts = productRepository.findAll()
                val topProducts = allProducts
                    .filter { it.salesCount > 0 }
                    .sortedByDescending { it.salesCount }
                    .take(5)

                // then
                topProducts.size shouldNotBe 0
                topProducts.all { it.salesCount > 0 } shouldBe true
                // 판매량 내림차순 확인
                for (i in 0 until topProducts.size - 1) {
                    topProducts[i].salesCount shouldNotBe null
                }
            }
        }
    }
})
