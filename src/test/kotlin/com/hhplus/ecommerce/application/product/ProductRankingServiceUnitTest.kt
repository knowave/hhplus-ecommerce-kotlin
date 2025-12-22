package com.hhplus.ecommerce.application.product

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import com.hhplus.ecommerce.domain.product.entity.RankingPeriod
import com.hhplus.ecommerce.domain.product.repository.ProductJpaRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.LocalDate
import java.util.UUID

class ProductRankingServiceUnitTest : DescribeSpec({

    lateinit var redisTemplate: RedisTemplate<String, String>
    lateinit var zSetOps: ZSetOperations<String, String>
    lateinit var productRepository: ProductJpaRepository
    lateinit var productRankingService: ProductRankingServiceImpl

    fun createProduct(
        id: UUID,
        name: String,
        price: Long,
        category: ProductCategory,
        salesCount: Int = 0
    ): Product {
        val product = Product(
            name = name,
            description = "$name 상세 설명",
            price = price,
            stock = 100,
            category = category,
            specifications = emptyMap(),
            salesCount = salesCount
        )
        // Reflection을 사용하여 id 설정
        val idField = product.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(product, id)
        return product
    }

    beforeEach {
        redisTemplate = mockk(relaxed = true)
        zSetOps = mockk(relaxed = true)
        productRepository = mockk(relaxed = true)

        every { redisTemplate.opsForZSet() } returns zSetOps

        productRankingService = ProductRankingServiceImpl(redisTemplate, productRepository)
    }

    describe("ProductRankingService 단위 테스트 - incrementOrderCount") {

        context("일간 랭킹 업데이트") {
            it("상품 주문 시 일간 랭킹에 주문 수량이 증가한다") {
                // given
                val productId = UUID.randomUUID()
                val quantity = 5
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                every { zSetOps.incrementScore(expectedKey, productId.toString(), quantity.toDouble()) } returns quantity.toDouble()

                // when
                productRankingService.incrementOrderCount(productId, quantity, RankingPeriod.DAILY)

                // then
                verify(exactly = 1) { zSetOps.incrementScore(expectedKey, productId.toString(), quantity.toDouble()) }
                verify(exactly = 1) { redisTemplate.expire(expectedKey, any()) }
            }

            it("특정 날짜를 지정하여 일간 랭킹을 업데이트할 수 있다") {
                // given
                val productId = UUID.randomUUID()
                val quantity = 3
                val specificDate = LocalDate.of(2025, 12, 1)
                val expectedKey = "product:ranking:daily:20251201"

                every { zSetOps.incrementScore(expectedKey, productId.toString(), quantity.toDouble()) } returns quantity.toDouble()

                // when
                productRankingService.incrementOrderCount(productId, quantity, RankingPeriod.DAILY, specificDate)

                // then
                verify(exactly = 1) { zSetOps.incrementScore(expectedKey, productId.toString(), quantity.toDouble()) }
            }

            it("동일 상품을 여러 번 주문하면 누적된다") {
                // given
                val productId = UUID.randomUUID()
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                every { zSetOps.incrementScore(expectedKey, productId.toString(), 2.0) } returns 2.0
                every { zSetOps.incrementScore(expectedKey, productId.toString(), 3.0) } returns 5.0

                // when
                productRankingService.incrementOrderCount(productId, 2, RankingPeriod.DAILY)
                productRankingService.incrementOrderCount(productId, 3, RankingPeriod.DAILY)

                // then
                verify(exactly = 1) { zSetOps.incrementScore(expectedKey, productId.toString(), 2.0) }
                verify(exactly = 1) { zSetOps.incrementScore(expectedKey, productId.toString(), 3.0) }
            }
        }

        context("주간 랭킹 업데이트") {
            it("상품 주문 시 주간 랭킹에 주문 수량이 증가한다") {
                // given
                val productId = UUID.randomUUID()
                val quantity = 10
                val today = LocalDate.now()
                val weekFields = java.time.temporal.WeekFields.ISO
                val year = today.get(weekFields.weekBasedYear())
                val week = today.get(weekFields.weekOfWeekBasedYear())
                val expectedKey = "product:ranking:weekly:$year-W${week.toString().padStart(2, '0')}"

                every { zSetOps.incrementScore(expectedKey, productId.toString(), quantity.toDouble()) } returns quantity.toDouble()

                // when
                productRankingService.incrementOrderCount(productId, quantity, RankingPeriod.WEEKLY)

                // then
                verify(exactly = 1) { zSetOps.incrementScore(expectedKey, productId.toString(), quantity.toDouble()) }
                verify(exactly = 1) { redisTemplate.expire(expectedKey, any()) }
            }
        }
    }

    describe("ProductRankingService 단위 테스트 - getRanking") {

        context("일간 랭킹 조회") {
            it("일간 랭킹을 조회할 수 있다") {
                // given
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                val product1Id = UUID.randomUUID()
                val product2Id = UUID.randomUUID()
                val product3Id = UUID.randomUUID()

                val product1 = createProduct(product1Id, "인기상품1", 50000L, ProductCategory.ELECTRONICS, 100)
                val product2 = createProduct(product2Id, "인기상품2", 30000L, ProductCategory.FASHION, 80)
                val product3 = createProduct(product3Id, "인기상품3", 20000L, ProductCategory.FOOD, 60)

                // Redis ZSet 결과 Mock
                val typedTuple1 = mockk<ZSetOperations.TypedTuple<String>>()
                val typedTuple2 = mockk<ZSetOperations.TypedTuple<String>>()
                val typedTuple3 = mockk<ZSetOperations.TypedTuple<String>>()

                every { typedTuple1.value } returns product1Id.toString()
                every { typedTuple1.score } returns 50.0
                every { typedTuple2.value } returns product2Id.toString()
                every { typedTuple2.score } returns 30.0
                every { typedTuple3.value } returns product3Id.toString()
                every { typedTuple3.score } returns 20.0

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) } returns setOf(typedTuple1, typedTuple2, typedTuple3)
                every { productRepository.findAllById(listOf(product1Id, product2Id, product3Id)) } returns listOf(product1, product2, product3)

                // when
                val result = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                // then
                result.period shouldBe "DAILY"
                result.rankings shouldHaveSize 3
                result.rankings[0].rank shouldBe 1
                result.rankings[0].productId shouldBe product1Id
                result.rankings[0].orderCount shouldBe 50
                result.rankings[1].rank shouldBe 2
                result.rankings[1].productId shouldBe product2Id
                result.rankings[1].orderCount shouldBe 30
                result.rankings[2].rank shouldBe 3
                result.rankings[2].productId shouldBe product3Id
                result.rankings[2].orderCount shouldBe 20
            }

            it("랭킹 데이터가 없으면 빈 목록을 반환한다") {
                // given
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) } returns emptySet()

                // when
                val result = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                // then
                result.period shouldBe "DAILY"
                result.rankings.shouldBeEmpty()
                result.totalCount shouldBe 0
            }

            it("limit 개수만큼만 조회한다") {
                // given
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                val product1Id = UUID.randomUUID()
                val product1 = createProduct(product1Id, "인기상품1", 50000L, ProductCategory.ELECTRONICS)

                val typedTuple1 = mockk<ZSetOperations.TypedTuple<String>>()
                every { typedTuple1.value } returns product1Id.toString()
                every { typedTuple1.score } returns 100.0

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 4) } returns setOf(typedTuple1)
                every { productRepository.findAllById(listOf(product1Id)) } returns listOf(product1)

                // when
                productRankingService.getRanking(RankingPeriod.DAILY, limit = 5)

                // then
                verify(exactly = 1) { zSetOps.reverseRangeWithScores(expectedKey, 0, 4) }
            }

            it("특정 날짜의 일간 랭킹을 조회할 수 있다") {
                // given
                val specificDate = LocalDate.of(2025, 12, 1)
                val expectedKey = "product:ranking:daily:20251201"

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) } returns emptySet()

                // when
                val result = productRankingService.getRanking(RankingPeriod.DAILY, date = specificDate, limit = 10)

                // then
                result.date shouldBe "2025-12-01"
                verify(exactly = 1) { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) }
            }
        }

        context("주간 랭킹 조회") {
            it("주간 랭킹을 조회할 수 있다") {
                // given
                val today = LocalDate.now()
                val weekFields = java.time.temporal.WeekFields.ISO
                val year = today.get(weekFields.weekBasedYear())
                val week = today.get(weekFields.weekOfWeekBasedYear())
                val expectedKey = "product:ranking:weekly:$year-W${week.toString().padStart(2, '0')}"

                val product1Id = UUID.randomUUID()
                val product1 = createProduct(product1Id, "주간인기상품", 100000L, ProductCategory.ELECTRONICS)

                val typedTuple1 = mockk<ZSetOperations.TypedTuple<String>>()
                every { typedTuple1.value } returns product1Id.toString()
                every { typedTuple1.score } returns 200.0

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) } returns setOf(typedTuple1)
                every { productRepository.findAllById(listOf(product1Id)) } returns listOf(product1)

                // when
                val result = productRankingService.getRanking(RankingPeriod.WEEKLY, limit = 10)

                // then
                result.period shouldBe "WEEKLY"
                result.rankings shouldHaveSize 1
                result.rankings[0].orderCount shouldBe 200
            }
        }

        context("예외 케이스") {
            it("DB에 존재하지 않는 상품은 랭킹에서 제외된다") {
                // given
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                val existingProductId = UUID.randomUUID()
                val deletedProductId = UUID.randomUUID()

                val existingProduct = createProduct(existingProductId, "존재하는상품", 50000L, ProductCategory.ELECTRONICS)

                val typedTuple1 = mockk<ZSetOperations.TypedTuple<String>>()
                val typedTuple2 = mockk<ZSetOperations.TypedTuple<String>>()

                every { typedTuple1.value } returns existingProductId.toString()
                every { typedTuple1.score } returns 100.0
                every { typedTuple2.value } returns deletedProductId.toString()
                every { typedTuple2.score } returns 50.0

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) } returns setOf(typedTuple1, typedTuple2)
                // 삭제된 상품은 DB에서 조회되지 않음
                every { productRepository.findAllById(listOf(existingProductId, deletedProductId)) } returns listOf(existingProduct)

                // when
                val result = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                // then
                result.rankings shouldHaveSize 1
                result.rankings[0].productId shouldBe existingProductId
            }

            it("잘못된 productId 형식은 무시된다") {
                // given
                val today = LocalDate.now()
                val expectedKey = "product:ranking:daily:${today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}"

                val validProductId = UUID.randomUUID()
                val validProduct = createProduct(validProductId, "유효한상품", 50000L, ProductCategory.ELECTRONICS)

                val typedTuple1 = mockk<ZSetOperations.TypedTuple<String>>()
                val typedTuple2 = mockk<ZSetOperations.TypedTuple<String>>()

                every { typedTuple1.value } returns validProductId.toString()
                every { typedTuple1.score } returns 100.0
                every { typedTuple2.value } returns "invalid-uuid-format"
                every { typedTuple2.score } returns 50.0

                every { zSetOps.reverseRangeWithScores(expectedKey, 0, 9) } returns setOf(typedTuple1, typedTuple2)
                every { productRepository.findAllById(listOf(validProductId)) } returns listOf(validProduct)

                // when
                val result = productRankingService.getRanking(RankingPeriod.DAILY, limit = 10)

                // then
                result.rankings shouldHaveSize 1
                result.rankings[0].productId shouldBe validProductId
            }
        }
    }

    describe("ProductRankingService 단위 테스트 - cleanupExpiredRankings") {

        context("만료된 랭킹 정리") {
            it("지정된 날짜 이전의 일간 및 주간 랭킹을 삭제한다") {
                // given
                val beforeDate = LocalDate.of(2025, 11, 1)

                every { redisTemplate.unlink(any<Collection<String>>()) } returns 42L

                // when
                productRankingService.cleanupExpiredRankings(beforeDate)

                // then
                // unlink 메서드가 한 번 호출되고, 30일간의 일간 랭킹 + 12주간의 주간 랭킹 키 목록을 전달받는지 확인
                verify(exactly = 1) {
                    redisTemplate.unlink(match<Collection<String>> { keys ->
                        val dailyKeys = keys.filter { it.contains("daily") }
                        val weeklyKeys = keys.filter { it.contains("weekly") }
                        dailyKeys.size == 30 && weeklyKeys.size == 12
                    })
                }
            }
        }
    }
})

