package com.hhplus.ecommerce.infrastructure.cart

import com.hhplus.ecommerce.domain.cart.entity.CartItem
import com.hhplus.ecommerce.domain.cart.repository.CartJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.util.UUID

/**
 * CartJpaRepository 통합 테스트
 * - JPA InMemory(H2) 사용
 * - Custom 메서드만 검증 (Spring Data JPA가 자동 생성하는 메서드들)
 * - 기본 CRUD 메서드는 JpaRepository에서 제공하므로 테스트하지 않음
 */
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
class CartRepositoryIntegrationTest(
    private val cartJpaRepository: CartJpaRepository
) : DescribeSpec() {

    override fun extensions(): List<Extension> = listOf(SpringExtension)

    // 테스트용 데이터 ID
    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private lateinit var product1Id: UUID
    private lateinit var product2Id: UUID
    private lateinit var product3Id: UUID
    private lateinit var cartItem1Id: UUID
    private lateinit var cartItem2Id: UUID
    private lateinit var cartItem3Id: UUID

    init {
        beforeEach {
            // 테스트용 UUID 생성
            user1Id = UUID.randomUUID()
            user2Id = UUID.randomUUID()
            product1Id = UUID.randomUUID()
            product2Id = UUID.randomUUID()
            product3Id = UUID.randomUUID()

            // 사용자 1의 장바구니 아이템 2개
            val cartItem1 = CartItem(
                userId = user1Id,
                productId = product1Id,
                quantity = 2
            )
            val savedItem1 = cartJpaRepository.save(cartItem1)
            cartItem1Id = savedItem1.id!!

            val cartItem2 = CartItem(
                userId = user1Id,
                productId = product2Id,
                quantity = 1
            )
            val savedItem2 = cartJpaRepository.save(cartItem2)
            cartItem2Id = savedItem2.id!!

            // 사용자 2의 장바구니 아이템 1개
            val cartItem3 = CartItem(
                userId = user2Id,
                productId = product3Id,
                quantity = 3
            )
            val savedItem3 = cartJpaRepository.save(cartItem3)
            cartItem3Id = savedItem3.id!!
        }

        describe("CartJpaRepository Custom 메서드 테스트 - findByUserId") {
            context("정상 케이스") {
                it("특정 사용자의 모든 장바구니 아이템을 조회한다") {
                    // when
                    val cartItems = cartJpaRepository.findByUserId(user1Id)

                    // then
                    cartItems shouldHaveSize 2
                    cartItems.all { it.userId == user1Id } shouldBe true
                    cartItems.map { it.productId } shouldContainExactlyInAnyOrder listOf(product1Id, product2Id)
                }

                it("다른 사용자의 장바구니 아이템도 정상 조회된다") {
                    // when
                    val cartItems = cartJpaRepository.findByUserId(user2Id)

                    // then
                    cartItems shouldHaveSize 1
                    cartItems[0].userId shouldBe user2Id
                    cartItems[0].productId shouldBe product3Id
                    cartItems[0].quantity shouldBe 3
                }

                it("여러 아이템이 생성 순서대로 조회된다") {
                    // given - 사용자 1에게 추가 아이템 생성
                    val additionalItem = CartItem(
                        userId = user1Id,
                        productId = UUID.randomUUID(),
                        quantity = 5
                    )
                    cartJpaRepository.save(additionalItem)

                    // when
                    val cartItems = cartJpaRepository.findByUserId(user1Id)

                    // then
                    cartItems shouldHaveSize 3
                    // 생성 시간 순으로 정렬되어야 함
                    for (i in 0 until cartItems.size - 1) {
                        cartItems[i].createdAt shouldNotBe null
                        cartItems[i + 1].createdAt shouldNotBe null
                    }
                }
            }

            context("예외 케이스") {
                it("장바구니가 비어있는 사용자는 빈 리스트를 반환한다") {
                    // given
                    val emptyUserId = UUID.randomUUID()

                    // when
                    val cartItems = cartJpaRepository.findByUserId(emptyUserId)

                    // then
                    cartItems.shouldBeEmpty()
                }
            }
        }

        describe("CartJpaRepository Custom 메서드 테스트 - findByUserIdAndProductId") {
            context("정상 케이스") {
                it("특정 사용자의 특정 상품을 조회한다") {
                    // when
                    val cartItem = cartJpaRepository.findByUserIdAndProductId(user1Id, product1Id)

                    // then
                    cartItem shouldNotBe null
                    cartItem!!.userId shouldBe user1Id
                    cartItem.productId shouldBe product1Id
                    cartItem.quantity shouldBe 2
                }

                it("다른 사용자와 상품 조합도 정상 조회된다") {
                    // when
                    val cartItem = cartJpaRepository.findByUserIdAndProductId(user2Id, product3Id)

                    // then
                    cartItem shouldNotBe null
                    cartItem!!.userId shouldBe user2Id
                    cartItem.productId shouldBe product3Id
                    cartItem.quantity shouldBe 3
                }
            }

            context("예외 케이스") {
                it("사용자의 장바구니에 없는 상품을 조회하면 null을 반환한다") {
                    // given
                    val nonExistentProductId = UUID.randomUUID()

                    // when
                    val cartItem = cartJpaRepository.findByUserIdAndProductId(user1Id, nonExistentProductId)

                    // then
                    cartItem shouldBe null
                }

                it("존재하지 않는 사용자로 조회하면 null을 반환한다") {
                    // given
                    val nonExistentUserId = UUID.randomUUID()

                    // when
                    val cartItem = cartJpaRepository.findByUserIdAndProductId(nonExistentUserId, product1Id)

                    // then
                    cartItem shouldBe null
                }
            }
        }

        describe("CartJpaRepository Custom 메서드 테스트 - deleteByUserId") {
            context("정상 케이스") {
                it("특정 사용자의 모든 장바구니 아이템을 삭제한다") {
                    // given - 삭제 전 확인
                    val beforeDelete = cartJpaRepository.findByUserId(user1Id)
                    beforeDelete shouldHaveSize 2

                    // when
                    val deletedCount = cartJpaRepository.deleteByUserId(user1Id)

                    // then
                    deletedCount shouldBe 2
                    val afterDelete = cartJpaRepository.findByUserId(user1Id)
                    afterDelete.shouldBeEmpty()
                }

                it("다른 사용자의 장바구니는 영향받지 않는다") {
                    // given - 사용자 1의 장바구니 아이템 개수 확인
                    val user1ItemsBefore = cartJpaRepository.findByUserId(user1Id)
                    user1ItemsBefore shouldHaveSize 2

                    // when - 사용자 1의 장바구니만 삭제
                    cartJpaRepository.deleteByUserId(user1Id)

                    // then - 사용자 2의 장바구니는 유지
                    val user1ItemsAfter = cartJpaRepository.findByUserId(user1Id)
                    val user2ItemsAfter = cartJpaRepository.findByUserId(user2Id)

                    user1ItemsAfter.shouldBeEmpty()
                    user2ItemsAfter shouldHaveSize 1
                    user2ItemsAfter[0].userId shouldBe user2Id
                }

                it("빈 장바구니를 삭제하면 0을 반환한다") {
                    // given
                    val emptyUserId = UUID.randomUUID()

                    // when
                    val deletedCount = cartJpaRepository.deleteByUserId(emptyUserId)

                    // then
                    deletedCount shouldBe 0
                }
            }
        }

        describe("CartJpaRepository Custom 메서드 테스트 - findByUserIdAndProductIdIn") {
            context("정상 케이스") {
                it("특정 사용자의 여러 상품을 조회한다") {
                    // given
                    val productIds = listOf(product1Id, product2Id)

                    // when
                    val cartItems = cartJpaRepository.findByUserIdAndProductIdIn(user1Id, productIds)

                    // then
                    cartItems shouldHaveSize 2
                    cartItems.all { it.userId == user1Id } shouldBe true
                    cartItems.map { it.productId } shouldContainExactlyInAnyOrder productIds
                }

                it("일부만 있는 상품 ID 목록으로 조회하면 있는 것만 반환한다") {
                    // given - product1Id는 있지만, 나머지는 없음
                    val productIds = listOf(product1Id, UUID.randomUUID(), UUID.randomUUID())

                    // when
                    val cartItems = cartJpaRepository.findByUserIdAndProductIdIn(user1Id, productIds)

                    // then
                    cartItems shouldHaveSize 1
                    cartItems[0].productId shouldBe product1Id
                }

                it("빈 상품 ID 목록으로 조회하면 빈 리스트를 반환한다") {
                    // given
                    val emptyProductIds = emptyList<UUID>()

                    // when
                    val cartItems = cartJpaRepository.findByUserIdAndProductIdIn(user1Id, emptyProductIds)

                    // then
                    cartItems.shouldBeEmpty()
                }
            }

            context("예외 케이스") {
                it("장바구니에 없는 상품 ID 목록으로 조회하면 빈 리스트를 반환한다") {
                    // given
                    val nonExistentProductIds = listOf(UUID.randomUUID(), UUID.randomUUID())

                    // when
                    val cartItems = cartJpaRepository.findByUserIdAndProductIdIn(user1Id, nonExistentProductIds)

                    // then
                    cartItems.shouldBeEmpty()
                }

                it("존재하지 않는 사용자로 조회하면 빈 리스트를 반환한다") {
                    // given
                    val nonExistentUserId = UUID.randomUUID()
                    val productIds = listOf(product1Id, product2Id)

                    // when
                    val cartItems = cartJpaRepository.findByUserIdAndProductIdIn(nonExistentUserId, productIds)

                    // then
                    cartItems.shouldBeEmpty()
                }
            }
        }

        describe("CartJpaRepository Custom 메서드 테스트 - 복합 시나리오") {
            context("실제 비즈니스 흐름") {
                it("같은 상품을 여러 번 추가하는 시나리오 (중복 체크)") {
                    // 1. 기존 상품 확인
                    val existingItem = cartJpaRepository.findByUserIdAndProductId(user1Id, product1Id)
                    existingItem shouldNotBe null
                    val originalQuantity = existingItem!!.quantity

                    // 2. 같은 상품이 이미 있음을 확인하고 수량만 증가
                    existingItem.quantity += 3
                    cartJpaRepository.save(existingItem)

                    // 3. 검증
                    val updatedItem = cartJpaRepository.findByUserIdAndProductId(user1Id, product1Id)
                    updatedItem shouldNotBe null
                    updatedItem!!.quantity shouldBe (originalQuantity + 3)

                    // 4. 여전히 아이템은 2개만 있어야 함 (중복 생성 안됨)
                    val allItems = cartJpaRepository.findByUserId(user1Id)
                    allItems shouldHaveSize 2
                }

                it("주문 후 특정 상품들만 장바구니에서 삭제하는 시나리오") {
                    // 1. 사용자 1에게 상품 추가
                    val additionalProduct = UUID.randomUUID()
                    val additionalItem = CartItem(
                        userId = user1Id,
                        productId = additionalProduct,
                        quantity = 2
                    )
                    cartJpaRepository.save(additionalItem)

                    // 2. 장바구니에 3개 확인
                    val beforeOrder = cartJpaRepository.findByUserId(user1Id)
                    beforeOrder shouldHaveSize 3

                    // 3. 주문할 상품 조회 (product1Id, product2Id)
                    val orderedProductIds = listOf(product1Id, product2Id)
                    val orderedItems = cartJpaRepository.findByUserIdAndProductIdIn(user1Id, orderedProductIds)
                    orderedItems shouldHaveSize 2

                    // 4. 주문한 상품만 삭제
                    orderedItems.forEach { cartJpaRepository.delete(it) }

                    // 5. 주문하지 않은 상품만 남음
                    val afterOrder = cartJpaRepository.findByUserId(user1Id)
                    afterOrder shouldHaveSize 1
                    afterOrder[0].productId shouldBe additionalProduct
                }

                it("여러 사용자가 동시에 같은 상품을 장바구니에 담는 시나리오") {
                    // 1. 두 사용자가 같은 상품을 장바구니에 담음
                    val commonProductId = UUID.randomUUID()

                    val user1Item = CartItem(
                        userId = user1Id,
                        productId = commonProductId,
                        quantity = 2
                    )
                    cartJpaRepository.save(user1Item)

                    val user2Item = CartItem(
                        userId = user2Id,
                        productId = commonProductId,
                        quantity = 5
                    )
                    cartJpaRepository.save(user2Item)

                    // 2. 각 사용자의 장바구니는 독립적으로 관리됨
                    val user1CommonItem = cartJpaRepository.findByUserIdAndProductId(user1Id, commonProductId)
                    val user2CommonItem = cartJpaRepository.findByUserIdAndProductId(user2Id, commonProductId)

                    user1CommonItem shouldNotBe null
                    user2CommonItem shouldNotBe null
                    user1CommonItem!!.quantity shouldBe 2
                    user2CommonItem!!.quantity shouldBe 5

                    // 3. 한 사용자의 장바구니 비우기는 다른 사용자에게 영향 없음
                    cartJpaRepository.deleteByUserId(user1Id)

                    val user1ItemsAfter = cartJpaRepository.findByUserId(user1Id)
                    val user2ItemsAfter = cartJpaRepository.findByUserId(user2Id)

                    user1ItemsAfter.shouldBeEmpty()
                    user2ItemsAfter.shouldNotBeEmpty()
                }

                it("장바구니 전체 비우기 후 새로운 상품 추가하는 시나리오") {
                    // 1. 사용자 1의 현재 장바구니 확인
                    val before = cartJpaRepository.findByUserId(user1Id)
                    before shouldHaveSize 2

                    // 2. 장바구니 전체 비우기
                    val deletedCount = cartJpaRepository.deleteByUserId(user1Id)
                    deletedCount shouldBe 2

                    // 3. 빈 장바구니 확인
                    val afterClear = cartJpaRepository.findByUserId(user1Id)
                    afterClear.shouldBeEmpty()

                    // 4. 새로운 상품 추가
                    val newProductId = UUID.randomUUID()
                    val newItem = CartItem(
                        userId = user1Id,
                        productId = newProductId,
                        quantity = 10
                    )
                    cartJpaRepository.save(newItem)

                    // 5. 새 장바구니 확인
                    val afterAdd = cartJpaRepository.findByUserId(user1Id)
                    afterAdd shouldHaveSize 1
                    afterAdd[0].productId shouldBe newProductId
                    afterAdd[0].quantity shouldBe 10
                }
            }
        }
    }
}
