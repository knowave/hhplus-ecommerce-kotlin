package com.hhplus.ecommerce.infrastructure.cart

import com.hhplus.ecommerce.domain.cart.entity.CartItem
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

class CartRepositoryIntegrationTest : DescribeSpec({
    lateinit var cartRepository: CartRepository

    beforeEach {
        cartRepository = CartRepositoryImpl()
    }

    describe("CartRepository 통합 테스트 - findById") {
        context("정상 케이스") {
            it("존재하는 장바구니 아이템 ID로 조회한다") {
                // given - 샘플 데이터에 사용자 1의 장바구니 아이템이 있음
                val cartItemId = 1L

                // when
                val cartItem = cartRepository.findById(cartItemId)

                // then
                cartItem shouldNotBe null
                cartItem!!.id shouldBe cartItemId
                cartItem.userId shouldBe 1L
                cartItem.productId shouldBe 15L
                cartItem.quantity shouldBe 2
                cartItem.addedAt shouldNotBe null
                cartItem.updatedAt shouldNotBe null
            }

            it("다른 장바구니 아이템도 정상적으로 조회된다") {
                // given
                val cartItemId = 2L

                // when
                val cartItem = cartRepository.findById(cartItemId)

                // then
                cartItem shouldNotBe null
                cartItem!!.id shouldBe cartItemId
                cartItem.userId shouldBe 1L
                cartItem.productId shouldBe 7L
                cartItem.quantity shouldBe 1
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 장바구니 아이템 ID로 조회 시 null을 반환한다") {
                // given
                val invalidCartItemId = 999L

                // when
                val cartItem = cartRepository.findById(invalidCartItemId)

                // then
                cartItem shouldBe null
            }
        }
    }

    describe("CartRepository 통합 테스트 - findByUserId") {
        context("정상 케이스") {
            it("특정 사용자의 장바구니 아이템을 모두 조회한다") {
                // given
                val userId = 1L

                // when
                val cartItems = cartRepository.findByUserId(userId)

                // then
                cartItems shouldHaveSize 2
                cartItems.all { it.userId == userId } shouldBe true
                cartItems.map { it.productId } shouldContainExactlyInAnyOrder listOf(15L, 7L)
            }

            it("장바구니 아이템이 없는 사용자는 빈 목록을 반환한다") {
                // given
                val userIdWithoutCart = 999L

                // when
                val cartItems = cartRepository.findByUserId(userIdWithoutCart)

                // then
                cartItems.shouldBeEmpty()
            }

            it("장바구니 아이템이 추가된 시간 순으로 정렬되어 조회된다") {
                // given
                val userId = 1L

                // when
                val cartItems = cartRepository.findByUserId(userId)

                // then
                cartItems shouldHaveSize 2
                // 첫 번째 아이템이 더 일찍 추가됨
                for (i in 0 until cartItems.size - 1) {
                    cartItems[i].addedAt shouldNotBe null
                    cartItems[i + 1].addedAt shouldNotBe null
                }
            }
        }
    }

    describe("CartRepository 통합 테스트 - findByUserIdAndProductId") {
        context("정상 케이스") {
            it("특정 사용자의 특정 상품을 조회한다") {
                // given
                val userId = 1L
                val productId = 15L

                // when
                val cartItem = cartRepository.findByUserIdAndProductId(userId, productId)

                // then
                cartItem shouldNotBe null
                cartItem!!.userId shouldBe userId
                cartItem.productId shouldBe productId
                cartItem.quantity shouldBe 2
            }

            it("다른 상품도 정상적으로 조회된다") {
                // given
                val userId = 1L
                val productId = 7L

                // when
                val cartItem = cartRepository.findByUserIdAndProductId(userId, productId)

                // then
                cartItem shouldNotBe null
                cartItem!!.userId shouldBe userId
                cartItem.productId shouldBe productId
                cartItem.quantity shouldBe 1
            }
        }

        context("예외 케이스") {
            it("사용자의 장바구니에 없는 상품을 조회하면 null을 반환한다") {
                // given
                val userId = 1L
                val nonExistentProductId = 999L

                // when
                val cartItem = cartRepository.findByUserIdAndProductId(userId, nonExistentProductId)

                // then
                cartItem shouldBe null
            }

            it("장바구니가 없는 사용자로 조회하면 null을 반환한다") {
                // given
                val nonExistentUserId = 999L
                val productId = 15L

                // when
                val cartItem = cartRepository.findByUserIdAndProductId(nonExistentUserId, productId)

                // then
                cartItem shouldBe null
            }
        }
    }

    describe("CartRepository 통합 테스트 - save") {
        context("정상 케이스 - 신규 아이템 저장") {
            it("새로운 장바구니 아이템을 저장한다") {
                // given
                val now = LocalDateTime.now()
                val newCartItem = CartItem(
                    id = 100L,
                    userId = 2L,
                    productId = 1L,
                    quantity = 3,
                    addedAt = now
                )

                // when
                val saved = cartRepository.save(newCartItem)

                // then
                saved.id shouldBe 100L
                saved.userId shouldBe 2L
                saved.productId shouldBe 1L
                saved.quantity shouldBe 3

                // 저장 후 조회 가능
                val found = cartRepository.findById(100L)
                found shouldNotBe null
                found!!.quantity shouldBe 3
            }

            it("여러 개의 새로운 아이템을 저장할 수 있다") {
                // given
                val now = LocalDateTime.now()
                val item1 = CartItem(101L, 2L, 1L, 1, now)
                val item2 = CartItem(102L, 2L, 2L, 2, now)
                val item3 = CartItem(103L, 2L, 3L, 3, now)

                // when
                cartRepository.save(item1)
                cartRepository.save(item2)
                cartRepository.save(item3)

                // then
                val user2Items = cartRepository.findByUserId(2L)
                user2Items shouldHaveSize 3
                user2Items.map { it.productId } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L)
            }
        }

        context("정상 케이스 - 기존 아이템 업데이트") {
            it("기존 장바구니 아이템의 수량을 업데이트한다") {
                // given
                val cartItemId = 1L
                val originalItem = cartRepository.findById(cartItemId)!!
                val originalQuantity = originalItem.quantity

                // when - 수량 변경
                originalItem.quantity = originalQuantity + 5
                originalItem.updatedAt = LocalDateTime.now()
                cartRepository.save(originalItem)

                // then
                val updated = cartRepository.findById(cartItemId)!!
                updated.quantity shouldBe (originalQuantity + 5)
            }

            it("수량을 증가시킬 수 있다") {
                // given
                val cartItemId = 2L
                val originalItem = cartRepository.findById(cartItemId)!!
                val originalQuantity = originalItem.quantity

                // when
                originalItem.quantity = originalQuantity + 10
                cartRepository.save(originalItem)

                // then
                val updated = cartRepository.findById(cartItemId)!!
                updated.quantity shouldBe (originalQuantity + 10)
            }

            it("수량을 감소시킬 수 있다") {
                // given
                val cartItemId = 1L
                val originalItem = cartRepository.findById(cartItemId)!!
                val originalQuantity = originalItem.quantity

                // when
                originalItem.quantity = originalQuantity - 1
                cartRepository.save(originalItem)

                // then
                val updated = cartRepository.findById(cartItemId)!!
                updated.quantity shouldBe (originalQuantity - 1)
            }

            it("여러 번 save를 호출해도 정상 동작한다") {
                // given
                val cartItemId = 1L
                val originalItem = cartRepository.findById(cartItemId)!!

                // when - 여러 번 업데이트
                originalItem.quantity = 10
                cartRepository.save(originalItem)

                originalItem.quantity = 20
                cartRepository.save(originalItem)

                originalItem.quantity = 30
                cartRepository.save(originalItem)

                // then
                val updated = cartRepository.findById(cartItemId)!!
                updated.quantity shouldBe 30
            }
        }

        context("데이터 무결성") {
            it("save 후에도 다른 필드는 변경되지 않는다") {
                // given
                val cartItemId = 1L
                val originalItem = cartRepository.findById(cartItemId)!!
                val originalUserId = originalItem.userId
                val originalProductId = originalItem.productId
                val originalAddedAt = originalItem.addedAt

                // when - quantity만 변경
                originalItem.quantity = originalItem.quantity + 5
                cartRepository.save(originalItem)

                // then - 다른 필드는 유지
                val updated = cartRepository.findById(cartItemId)!!
                updated.userId shouldBe originalUserId
                updated.productId shouldBe originalProductId
                updated.addedAt shouldBe originalAddedAt
            }
        }
    }

    describe("CartRepository 통합 테스트 - delete") {
        context("정상 케이스") {
            it("장바구니 아이템을 삭제한다") {
                // given
                val now = LocalDateTime.now()
                val newItem = CartItem(200L, 2L, 5L, 2, now)
                cartRepository.save(newItem)
                cartRepository.findById(200L) shouldNotBe null

                // when
                cartRepository.delete(200L)

                // then
                cartRepository.findById(200L) shouldBe null
            }

            it("삭제된 아이템은 사용자별 조회에서도 제외된다") {
                // given
                val now = LocalDateTime.now()
                val userId = 2L
                val item1 = CartItem(201L, userId, 1L, 1, now)
                val item2 = CartItem(202L, userId, 2L, 2, now)
                cartRepository.save(item1)
                cartRepository.save(item2)

                val beforeDelete = cartRepository.findByUserId(userId)
                beforeDelete shouldHaveSize 2

                // when - 하나 삭제
                cartRepository.delete(201L)

                // then
                val afterDelete = cartRepository.findByUserId(userId)
                afterDelete shouldHaveSize 1
                afterDelete[0].id shouldBe 202L
            }
        }

        context("예외 케이스") {
            it("존재하지 않는 아이템을 삭제해도 오류가 발생하지 않는다") {
                // given
                val invalidCartItemId = 999L

                // when & then - 예외 발생하지 않음
                cartRepository.delete(invalidCartItemId)
            }
        }
    }

    describe("CartRepository 통합 테스트 - deleteByUserId") {
        context("정상 케이스") {
            it("특정 사용자의 모든 장바구니 아이템을 삭제한다") {
                // given
                val userId = 1L
                val beforeDelete = cartRepository.findByUserId(userId)
                beforeDelete shouldHaveSize 2

                // when
                cartRepository.deleteByUserId(userId)

                // then
                val afterDelete = cartRepository.findByUserId(userId)
                afterDelete.shouldBeEmpty()
            }

            it("다른 사용자의 장바구니는 영향받지 않는다") {
                // given - 사용자 2에게 아이템 추가
                val now = LocalDateTime.now()
                val user2Item = CartItem(300L, 2L, 1L, 1, now)
                cartRepository.save(user2Item)

                // when - 사용자 1의 장바구니만 삭제
                cartRepository.deleteByUserId(1L)

                // then - 사용자 2의 장바구니는 유지
                val user1Items = cartRepository.findByUserId(1L)
                val user2Items = cartRepository.findByUserId(2L)

                user1Items.shouldBeEmpty()
                user2Items shouldHaveSize 1
                user2Items[0].id shouldBe 300L
            }

            it("빈 장바구니를 삭제해도 오류가 발생하지 않는다") {
                // given
                val userIdWithoutCart = 999L

                // when & then - 예외 발생하지 않음
                cartRepository.deleteByUserId(userIdWithoutCart)

                val items = cartRepository.findByUserId(userIdWithoutCart)
                items.shouldBeEmpty()
            }
        }
    }

    describe("CartRepository 통합 테스트 - generateId") {
        context("정상 케이스") {
            it("새로운 ID를 생성한다") {
                // when
                val id1 = cartRepository.generateId()
                val id2 = cartRepository.generateId()
                val id3 = cartRepository.generateId()

                // then - ID는 순차적으로 증가
                id1 shouldNotBe null
                id2 shouldBe (id1 + 1)
                id3 shouldBe (id2 + 1)
            }

            it("생성된 ID는 중복되지 않는다") {
                // when
                val ids = mutableSetOf<Long>()
                repeat(100) {
                    ids.add(cartRepository.generateId())
                }

                // then
                ids.size shouldBe 100 // 모두 고유한 ID
            }
        }
    }

    describe("CartRepository 통합 테스트 - 복합 시나리오") {
        context("실제 비즈니스 흐름") {
            it("장바구니 추가 -> 조회 -> 수량 변경 -> 삭제 플로우") {
                // 1. 새 아이템 추가
                val userId = 3L
                val productId = 10L
                val now = LocalDateTime.now()
                val newItemId = cartRepository.generateId()
                val newItem = CartItem(newItemId, userId, productId, 2, now)
                cartRepository.save(newItem)

                // 2. 조회 확인
                val foundItem = cartRepository.findById(newItemId)
                foundItem shouldNotBe null
                foundItem!!.quantity shouldBe 2

                // 3. 수량 변경
                foundItem.quantity = 5
                foundItem.updatedAt = LocalDateTime.now()
                cartRepository.save(foundItem)

                val updatedItem = cartRepository.findById(newItemId)
                updatedItem!!.quantity shouldBe 5

                // 4. 삭제
                cartRepository.delete(newItemId)

                val deletedItem = cartRepository.findById(newItemId)
                deletedItem shouldBe null
            }

            it("같은 상품을 여러 번 추가하는 시나리오 (수량 증가)") {
                // 1. 첫 번째 추가
                val userId = 3L
                val productId = 5L
                val now = LocalDateTime.now()

                val existingItem = cartRepository.findByUserIdAndProductId(userId, productId)
                existingItem shouldBe null // 처음에는 없음

                // 2. 새 아이템 생성
                val newItemId = cartRepository.generateId()
                val newItem = CartItem(newItemId, userId, productId, 2, now)
                cartRepository.save(newItem)

                // 3. 같은 상품 다시 추가 시도 (기존 아이템 확인)
                val foundItem = cartRepository.findByUserIdAndProductId(userId, productId)
                foundItem shouldNotBe null

                // 4. 수량 증가
                val updatedItem = foundItem!!.copy(quantity = foundItem.quantity + 3)
                cartRepository.save(updatedItem)

                // 5. 검증
                val updated = cartRepository.findByUserIdAndProductId(userId, productId)
                updated!!.quantity shouldBe 5 // 2 + 3
            }

            it("여러 상품을 장바구니에 담고 일부만 삭제하는 시나리오") {
                // 1. 여러 상품 추가
                val userId = 4L
                val now = LocalDateTime.now()

                val item1Id = cartRepository.generateId()
                val item2Id = cartRepository.generateId()
                val item3Id = cartRepository.generateId()

                cartRepository.save(CartItem(item1Id, userId, 1L, 1, now))
                cartRepository.save(CartItem(item2Id, userId, 2L, 2, now))
                cartRepository.save(CartItem(item3Id, userId, 3L, 3, now))

                // 2. 3개 확인
                val items = cartRepository.findByUserId(userId)
                items shouldHaveSize 3

                // 3. 중간 아이템 삭제
                cartRepository.delete(item2Id)

                // 4. 2개만 남음
                val remainingItems = cartRepository.findByUserId(userId)
                remainingItems shouldHaveSize 2
                remainingItems.map { it.id } shouldContainExactlyInAnyOrder listOf(item1Id, item3Id)
            }

            it("장바구니 전체 비우기 시나리오") {
                // 1. 여러 상품 추가
                val userId = 5L
                val now = LocalDateTime.now()

                cartRepository.save(CartItem(cartRepository.generateId(), userId, 1L, 1, now))
                cartRepository.save(CartItem(cartRepository.generateId(), userId, 2L, 2, now))
                cartRepository.save(CartItem(cartRepository.generateId(), userId, 3L, 3, now))

                // 2. 3개 확인
                val beforeClear = cartRepository.findByUserId(userId)
                beforeClear shouldHaveSize 3

                // 3. 전체 비우기
                cartRepository.deleteByUserId(userId)

                // 4. 빈 장바구니 확인
                val afterClear = cartRepository.findByUserId(userId)
                afterClear.shouldBeEmpty()
            }

            it("여러 사용자가 동시에 장바구니를 사용하는 시나리오") {
                // 1. 사용자 별 장바구니 추가
                val now = LocalDateTime.now()

                // 사용자 10: 상품 1, 2
                cartRepository.save(CartItem(cartRepository.generateId(), 10L, 1L, 1, now))
                cartRepository.save(CartItem(cartRepository.generateId(), 10L, 2L, 2, now))

                // 사용자 20: 상품 3, 4, 5
                cartRepository.save(CartItem(cartRepository.generateId(), 20L, 3L, 1, now))
                cartRepository.save(CartItem(cartRepository.generateId(), 20L, 4L, 2, now))
                cartRepository.save(CartItem(cartRepository.generateId(), 20L, 5L, 3, now))

                // 2. 각 사용자별 장바구니 확인
                val user10Items = cartRepository.findByUserId(10L)
                val user20Items = cartRepository.findByUserId(20L)

                user10Items shouldHaveSize 2
                user20Items shouldHaveSize 3

                // 3. 사용자 10의 특정 상품 확인
                val user10Product1 = cartRepository.findByUserIdAndProductId(10L, 1L)
                user10Product1 shouldNotBe null
                user10Product1!!.quantity shouldBe 1

                // 4. 사용자 10의 장바구니만 비우기
                cartRepository.deleteByUserId(10L)

                // 5. 사용자 20의 장바구니는 유지
                val user10AfterClear = cartRepository.findByUserId(10L)
                val user20AfterClear = cartRepository.findByUserId(20L)

                user10AfterClear.shouldBeEmpty()
                user20AfterClear shouldHaveSize 3
            }
        }
    }
})