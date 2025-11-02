package com.hhplus.ecommerce.model.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserUnitTest : DescribeSpec({
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    describe("User 도메인 모델 단위 테스트") {
        context("User 객체 생성") {
            it("User 객체가 정상적으로 생성된다") {
                // given
                val userId = 1L
                val initialBalance = 10000L
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val user = User(
                    id = userId,
                    balance = initialBalance,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                user shouldNotBe null
                user.id shouldBe userId
                user.balance shouldBe initialBalance
                user.createdAt shouldBe now
                user.updatedAt shouldBe now
            }

            it("0원의 잔액으로 User를 생성할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val user = User(
                    id = 1L,
                    balance = 0L,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                user.balance shouldBe 0L
            }

            it("음수 잔액으로 User를 생성할 수 있다 (비즈니스 검증은 Service 레이어)") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)

                // when
                val user = User(
                    id = 1L,
                    balance = -1000L,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                user.balance shouldBe -1000L
            }
        }

        context("User 속성 변경") {
            it("User의 잔액을 변경할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = 1L,
                    balance = 10000L,
                    createdAt = now,
                    updatedAt = now
                )
                val newBalance = 20000L

                // when
                user.balance = newBalance

                // then
                user.balance shouldBe newBalance
            }

            it("User의 updatedAt을 변경할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = 1L,
                    balance = 10000L,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                Thread.sleep(10) // 시간 차이를 만들기 위해
                val newUpdatedAt = LocalDateTime.now().format(dateFormatter)
                user.updatedAt = newUpdatedAt

                // then
                user.updatedAt shouldNotBe now
                user.updatedAt shouldBe newUpdatedAt
            }
        }

        context("data class 동작") {
            it("동일한 값을 가진 User 객체는 같다고 판단된다 (equals)") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val user1 = User(
                    id = 1L,
                    balance = 10000L,
                    createdAt = now,
                    updatedAt = now
                )
                val user2 = User(
                    id = 1L,
                    balance = 10000L,
                    createdAt = now,
                    updatedAt = now
                )

                // then
                user1 shouldBe user2
            }

            it("copy() 메서드로 일부 속성만 변경한 새 객체를 생성할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val user = User(
                    id = 1L,
                    balance = 10000L,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                val copiedUser = user.copy(balance = 20000L)

                // then
                copiedUser.id shouldBe user.id
                copiedUser.balance shouldBe 20000L
                copiedUser.createdAt shouldBe user.createdAt
                copiedUser.updatedAt shouldBe user.updatedAt
                copiedUser shouldNotBe user // balance가 다르므로 같지 않음
            }
        }
    }
})