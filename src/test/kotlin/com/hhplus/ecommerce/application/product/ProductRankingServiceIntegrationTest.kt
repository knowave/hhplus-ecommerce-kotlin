package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@ComponentScan(basePackages = ["com.hhplus.ecommerce"])
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    ]
)
@Import(
    com.hhplus.ecommerce.config.EmbeddedRedisConfig::class,
    com.hhplus.ecommerce.config.TestRedisConfig::class
)
class ProductRankingServiceIntegrationTest(
    private val productRankingService: ProductRankingService,
    private val productRepository: ProductJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>
) : DescribeSpec() {
    override fun extensions(): List<Extension> = listOf(SpringExtension)

    private val testProducts = mutableListOf<Product>()

    init {
        beforeEach {
            // Redis 데이터 초기화
            redisTemplate.keys("product:ranking:*")?.forEach { key ->
                redisTemplate.delete(key)
            }

            // 테스트용 상품 생성
            testProducts.clear()

            val productsToCreate = listOf(
                Product(
                    name = "노트북 Pro",
                    description = "고성능 노트북",
                    price = 1500000L,
                    stock = 50,
                    category = ProductCategory.ELECTRONICS,
                    specifications = mapOf("cpu" to "Intel i7"),
                    salesCount = 100
                ),
                Product(
                    name = "스마트폰 Ultra",
                    description = "최신 스마트폰",
                    price = 1200000L,
                    stock = 100,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 150
                ),
                Product(
                    name = "무선 이어폰",
                    description = "노이즈 캔슬링",
                    price = 250000L,
                    stock = 200,
                    category = ProductCategory.ELECTRONICS,
                    specifications = emptyMap(),
                    salesCount = 200
                ),
                Product(
                    name = "운동화 ABC",
                    description = "편안한 운동화",
                    price = 89000L,
                    stock = 150,
                    category = ProductCategory.FASHION,
                    specifications = emptyMap(),
                    salesCount = 80
                ),
                Product(
                    name = "유기농 쌀 10kg",
                    description = "국내산 유기농",
                    price = 45000L,
                    stock = 500,
                    category = ProductCategory.FOOD,
                    specifications = emptyMap(),
                    salesCount = 300
                )
            )

            productsToCreate.forEach { product ->
                val saved = productRepository.save(product)
                testProducts.add(saved)
            }
        }

        afterEach {
            // Redis 데이터 정리
            redisTemplate.keys("product:ranking:*")?.forEach { key ->
                redisTemplate.delete(key)
            }
            productRepository.deleteAll()
        }

        describe("ProductRankingService 통합 테스트 - 일간 랭킹") {

            context("상품 주문 시 일간 랭킹 업데이트") {
                it("상품 주문 시 일간 랭킹에 반영된다") {
                    // given
                    val product = testProducts[0]
                    val quantity = 5

                    // when
                    productRankingService.incrementOrderCount(
                        productId = product.id!!,
                        quantity = quantity,
                        period = RankingPeriod.DAILY
                    )

                    // then
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)
                    ranking.rankings shouldHaveSize 1
                    ranking.rankings[0].productId shouldBe product.id
                    ranking.rankings[0].orderCount shouldBe 5
                    ranking.rankings[0].productName shouldBe "노트북 Pro"
                }

                it("동일 상품을 여러 번 주문하면 주문 수량이 누적된다") {
                    // given
                    val product = testProducts[0]

                    // when
                    productRankingService.incrementOrderCount(product.id!!, 3, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product.id!!, 2, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product.id!!, 5, RankingPeriod.DAILY)

                    // then
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)
                    ranking.rankings shouldHaveSize 1
                    ranking.rankings[0].orderCount shouldBe 10 // 3 + 2 + 5
                }

                it("여러 상품 주문 시 주문 수량 순으로 랭킹이 정렬된다") {
                    // given
                    val product1 = testProducts[0] // 노트북
                    val product2 = testProducts[1] // 스마트폰
                    val product3 = testProducts[2] // 이어폰

                    // when
                    productRankingService.incrementOrderCount(product1.id!!, 10, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product2.id!!, 30, RankingPeriod.DAILY) // 1위
                    productRankingService.incrementOrderCount(product3.id!!, 20, RankingPeriod.DAILY) // 2위

                    // then
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)
                    ranking.rankings shouldHaveSize 3

                    // 주문 수량 내림차순 정렬 확인
                    ranking.rankings[0].productId shouldBe product2.id // 30개
                    ranking.rankings[0].rank shouldBe 1
                    ranking.rankings[0].orderCount shouldBe 30

                    ranking.rankings[1].productId shouldBe product3.id // 20개
                    ranking.rankings[1].rank shouldBe 2
                    ranking.rankings[1].orderCount shouldBe 20

                    ranking.rankings[2].productId shouldBe product1.id // 10개
                    ranking.rankings[2].rank shouldBe 3
                    ranking.rankings[2].orderCount shouldBe 10
                }

                it("limit 개수만큼만 랭킹을 조회한다") {
                    // given
                    testProducts.forEachIndexed { index, product ->
                        productRankingService.incrementOrderCount(
                            product.id!!,
                            (index + 1) * 10,
                            RankingPeriod.DAILY
                        )
                    }

                    // when
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 3)

                    // then
                    ranking.rankings shouldHaveSize 3
                    ranking.totalCount shouldBe 3
                }
            }

            context("특정 날짜 일간 랭킹") {
                it("특정 날짜의 일간 랭킹을 조회할 수 있다") {
                    // given
                    val product = testProducts[0]
                    val specificDate = LocalDate.of(2025, 12, 1)

                    productRankingService.incrementOrderCount(
                        product.id!!,
                        15,
                        RankingPeriod.DAILY,
                        specificDate
                    )

                    // when
                    val ranking = productRankingService.getRanking(
                        RankingPeriod.DAILY,
                        date = specificDate,
                        limit = 10
                    )

                    // then
                    ranking.date shouldBe "2025-12-01"
                    ranking.rankings shouldHaveSize 1
                    ranking.rankings[0].orderCount shouldBe 15
                }

                it("다른 날짜의 랭킹은 서로 독립적이다") {
                    // given
                    val product = testProducts[0]
                    val date1 = LocalDate.of(2025, 12, 1)
                    val date2 = LocalDate.of(2025, 12, 2)

                    productRankingService.incrementOrderCount(product.id!!, 10, RankingPeriod.DAILY, date1)
                    productRankingService.incrementOrderCount(product.id!!, 20, RankingPeriod.DAILY, date2)

                    // when
                    val ranking1 = productRankingService.getRanking(RankingPeriod.DAILY, date = date1, limit = 10)
                    val ranking2 = productRankingService.getRanking(RankingPeriod.DAILY, date = date2, limit = 10)

                    // then
                    ranking1.rankings[0].orderCount shouldBe 10
                    ranking2.rankings[0].orderCount shouldBe 20
                }
            }

            context("랭킹 데이터가 없는 경우") {
                it("랭킹 데이터가 없으면 빈 목록을 반환한다") {
                    // when
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                    // then
                    ranking.rankings.shouldBeEmpty()
                    ranking.totalCount shouldBe 0
                    ranking.period shouldBe "DAILY"
                }
            }
        }

        describe("ProductRankingService 통합 테스트 - 주간 랭킹") {

            context("상품 주문 시 주간 랭킹 업데이트") {
                it("상품 주문 시 주간 랭킹에 반영된다") {
                    // given
                    val product = testProducts[0]
                    val quantity = 10

                    // when
                    productRankingService.incrementOrderCount(
                        productId = product.id!!,
                        quantity = quantity,
                        period = RankingPeriod.WEEKLY
                    )

                    // then
                    val ranking = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)
                    ranking.period shouldBe "WEEKLY"
                    ranking.rankings shouldHaveSize 1
                    ranking.rankings[0].productId shouldBe product.id
                    ranking.rankings[0].orderCount shouldBe 10
                }

                it("같은 주 내 여러 번 주문하면 주문 수량이 누적된다") {
                    // given
                    val product = testProducts[0]

                    // when
                    productRankingService.incrementOrderCount(product.id!!, 5, RankingPeriod.WEEKLY)
                    productRankingService.incrementOrderCount(product.id!!, 10, RankingPeriod.WEEKLY)
                    productRankingService.incrementOrderCount(product.id!!, 15, RankingPeriod.WEEKLY)

                    // then
                    val ranking = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)
                    ranking.rankings shouldHaveSize 1
                    ranking.rankings[0].orderCount shouldBe 30 // 5 + 10 + 15
                }

                it("주간 랭킹도 주문 수량 순으로 정렬된다") {
                    // given
                    val product1 = testProducts[0]
                    val product2 = testProducts[1]
                    val product3 = testProducts[2]

                    // when
                    productRankingService.incrementOrderCount(product1.id!!, 50, RankingPeriod.WEEKLY)
                    productRankingService.incrementOrderCount(product2.id!!, 100, RankingPeriod.WEEKLY) // 1위
                    productRankingService.incrementOrderCount(product3.id!!, 75, RankingPeriod.WEEKLY) // 2위

                    // then
                    val ranking = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)
                    ranking.rankings shouldHaveSize 3

                    ranking.rankings[0].productId shouldBe product2.id // 100개
                    ranking.rankings[0].rank shouldBe 1
                    ranking.rankings[1].productId shouldBe product3.id // 75개
                    ranking.rankings[1].rank shouldBe 2
                    ranking.rankings[2].productId shouldBe product1.id // 50개
                    ranking.rankings[2].rank shouldBe 3
                }
            }

            context("주간 랭킹 날짜 형식") {
                it("주간 랭킹의 날짜는 'YYYY-Www' 형식이다") {
                    // given
                    val product = testProducts[0]
                    productRankingService.incrementOrderCount(product.id!!, 5, RankingPeriod.WEEKLY)

                    // when
                    val ranking = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)

                    // then
                    ranking.date shouldNotBe null
                    ranking.date.matches(Regex("\\d{4}-W\\d{2}")) shouldBe true
                }
            }
        }

        describe("ProductRankingService 통합 테스트 - 일간/주간 랭킹 동시 업데이트") {

            context("주문 시 일간과 주간 랭킹 동시 업데이트") {
                it("하나의 주문으로 일간과 주간 랭킹이 모두 업데이트된다") {
                    // given
                    val product = testProducts[0]
                    val quantity = 10

                    // when - 실제 주문 시나리오처럼 일간/주간 모두 업데이트
                    productRankingService.incrementOrderCount(product.id!!, quantity, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product.id!!, quantity, RankingPeriod.WEEKLY)

                    // then
                    val dailyRanking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)
                    val weeklyRanking = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)

                    dailyRanking.rankings shouldHaveSize 1
                    dailyRanking.rankings[0].orderCount shouldBe 10

                    weeklyRanking.rankings shouldHaveSize 1
                    weeklyRanking.rankings[0].orderCount shouldBe 10
                }

                it("여러 상품 주문 시 일간과 주간 랭킹이 각각 정확하게 반영된다") {
                    // given
                    val product1 = testProducts[0]
                    val product2 = testProducts[1]

                    // when - product1: 일간 5개, 주간 5개 / product2: 일간 10개, 주간 10개
                    productRankingService.incrementOrderCount(product1.id!!, 5, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product1.id!!, 5, RankingPeriod.WEEKLY)

                    productRankingService.incrementOrderCount(product2.id!!, 10, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product2.id!!, 10, RankingPeriod.WEEKLY)

                    // then
                    val dailyRanking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)
                    val weeklyRanking = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)

                    // 일간 랭킹: product2(10) > product1(5)
                    dailyRanking.rankings[0].productId shouldBe product2.id
                    dailyRanking.rankings[0].orderCount shouldBe 10
                    dailyRanking.rankings[1].productId shouldBe product1.id
                    dailyRanking.rankings[1].orderCount shouldBe 5

                    // 주간 랭킹: product2(10) > product1(5)
                    weeklyRanking.rankings[0].productId shouldBe product2.id
                    weeklyRanking.rankings[0].orderCount shouldBe 10
                    weeklyRanking.rankings[1].productId shouldBe product1.id
                    weeklyRanking.rankings[1].orderCount shouldBe 5
                }
            }
        }

        describe("ProductRankingService 통합 테스트 - 랭킹 응답 데이터 검증") {

            context("랭킹 응답에 상품 정보가 포함된다") {
                it("랭킹 조회 시 상품의 상세 정보가 포함된다") {
                    // given
                    val product = testProducts[0] // 노트북 Pro
                    productRankingService.incrementOrderCount(product.id!!, 5, RankingPeriod.DAILY)

                    // when
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                    // then
                    val rankedProduct = ranking.rankings[0]
                    rankedProduct.productId shouldBe product.id
                    rankedProduct.productName shouldBe "노트북 Pro"
                    rankedProduct.category shouldBe "ELECTRONICS"
                    rankedProduct.price shouldBe 1500000L
                    rankedProduct.salesCount shouldBe 100 // DB의 salesCount
                    rankedProduct.orderCount shouldBe 5   // Redis의 주문 수량
                }
            }

            context("삭제된 상품 처리") {
                it("DB에서 삭제된 상품은 랭킹에서 제외된다") {
                    // given
                    val product1 = testProducts[0]
                    val product2 = testProducts[1]

                    productRankingService.incrementOrderCount(product1.id!!, 10, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product2.id!!, 20, RankingPeriod.DAILY)

                    // product1을 DB에서 삭제
                    productRepository.delete(product1)

                    // when
                    val ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                    // then - product1은 제외되고 product2만 조회됨
                    ranking.rankings shouldHaveSize 1
                    ranking.rankings[0].productId shouldBe product2.id
                }
            }
        }

        describe("ProductRankingService 통합 테스트 - 실제 시나리오") {

            context("쇼핑몰 일간 베스트 상품 시나리오") {
                it("오늘 하루 동안 가장 많이 팔린 상품 TOP 3을 조회한다") {
                    // given - 오늘 주문 시뮬레이션
                    val laptop = testProducts[0]      // 노트북
                    val phone = testProducts[1]       // 스마트폰
                    val earphone = testProducts[2]    // 이어폰
                    val shoes = testProducts[3]       // 운동화
                    val rice = testProducts[4]        // 쌀

                    // 오늘 주문량
                    productRankingService.incrementOrderCount(laptop.id!!, 15, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(phone.id!!, 50, RankingPeriod.DAILY)   // 1위
                    productRankingService.incrementOrderCount(earphone.id!!, 30, RankingPeriod.DAILY) // 2위
                    productRankingService.incrementOrderCount(shoes.id!!, 20, RankingPeriod.DAILY)    // 3위
                    productRankingService.incrementOrderCount(rice.id!!, 10, RankingPeriod.DAILY)

                    // when
                    val top3 = productRankingService.getRanking(RankingPeriod.DAILY, limit = 3)

                    // then
                    top3.rankings shouldHaveSize 3
                    top3.rankings[0].productName shouldBe "스마트폰 Ultra"
                    top3.rankings[0].orderCount shouldBe 50
                    top3.rankings[1].productName shouldBe "무선 이어폰"
                    top3.rankings[1].orderCount shouldBe 30
                    top3.rankings[2].productName shouldBe "운동화 ABC"
                    top3.rankings[2].orderCount shouldBe 20
                }
            }

            context("쇼핑몰 주간 베스트 상품 시나리오") {
                it("이번 주 가장 많이 팔린 상품 TOP 5를 조회한다") {
                    // given - 이번 주 주문 시뮬레이션
                    testProducts.forEachIndexed { index, product ->
                        val orderCount = (5 - index) * 20 // 100, 80, 60, 40, 20
                        productRankingService.incrementOrderCount(product.id!!, orderCount, RankingPeriod.WEEKLY)
                    }

                    // when
                    val top5 = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 5)

                    // then
                    top5.rankings shouldHaveSize 5
                    top5.rankings[0].orderCount shouldBe 100
                    top5.rankings[1].orderCount shouldBe 80
                    top5.rankings[2].orderCount shouldBe 60
                    top5.rankings[3].orderCount shouldBe 40
                    top5.rankings[4].orderCount shouldBe 20
                }
            }

            context("실시간 랭킹 업데이트 시나리오") {
                it("주문이 들어올 때마다 실시간으로 랭킹이 변경된다") {
                    // given
                    val product1 = testProducts[0]
                    val product2 = testProducts[1]

                    // 초기 상태: product1이 1위
                    productRankingService.incrementOrderCount(product1.id!!, 100, RankingPeriod.DAILY)
                    productRankingService.incrementOrderCount(product2.id!!, 50, RankingPeriod.DAILY)

                    var ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 2)
                    ranking.rankings[0].productId shouldBe product1.id

                    // when - product2에 대량 주문이 들어옴
                    productRankingService.incrementOrderCount(product2.id!!, 100, RankingPeriod.DAILY)

                    // then - product2가 1위로 변경됨
                    ranking = productRankingService.getRanking(RankingPeriod.DAILY, limit = 2)
                    ranking.rankings[0].productId shouldBe product2.id // 150개
                    ranking.rankings[0].orderCount shouldBe 150
                    ranking.rankings[1].productId shouldBe product1.id // 100개
                    ranking.rankings[1].orderCount shouldBe 100
                }
            }
        }
    }
}

