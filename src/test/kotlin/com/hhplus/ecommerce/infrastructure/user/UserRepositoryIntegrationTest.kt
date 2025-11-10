package com.hhplus.ecommerce.infrastructure.user

import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.repository.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserRepositoryIntegrationTest : DescribeSpec({
    lateinit var userRepository: UserRepository
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    beforeEach {
        // 실제 구현체 사용 (인메모리)
        userRepository = UserRepositoryImpl()
    }

    describe("UserRepository 통합 테스트") {
        context("findById - 사용자 조회") {
            it("존재하는 사용자 ID로 조회하면 사용자 정보를 반환한다") {
                // given
                val userId = 1L

                // when
                val user = userRepository.findById(userId)

                // then
                user shouldNotBe null
                user!!.id shouldBe userId
                user.balance shouldBe 50000L
            }

            it("존재하지 않는 사용자 ID로 조회하면 null을 반환한다") {
                // given
                val nonExistentId = 999L

                // when
                val user = userRepository.findById(nonExistentId)

                // then
                user shouldBe null
            }
        }

        context("save - 사용자 저장") {
            it("새로운 사용자를 저장할 수 있다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val newUser = User(
                    id = 100L,
                    balance = 75000L,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                val savedUser = userRepository.save(newUser)

                // then
                savedUser.id shouldBe 100L
                savedUser.balance shouldBe 75000L

                // 저장된 사용자를 다시 조회하여 확인
                val foundUser = userRepository.findById(100L)
                foundUser shouldNotBe null
                foundUser!!.balance shouldBe 75000L
            }

            it("기존 사용자의 정보를 업데이트할 수 있다") {
                // given
                val userId = 1L
                val existingUser = userRepository.findById(userId)!!
                val updatedBalance = existingUser.balance + 10000L
                val updatedAt = LocalDateTime.now().format(dateFormatter)

                existingUser.balance = updatedBalance
                existingUser.updatedAt = updatedAt

                // when
                val savedUser = userRepository.save(existingUser)

                // then
                savedUser.balance shouldBe updatedBalance
                savedUser.updatedAt shouldBe updatedAt

                // 다시 조회하여 업데이트 확인
                val foundUser = userRepository.findById(userId)
                foundUser shouldNotBe null
                foundUser!!.balance shouldBe updatedBalance
            }
        }

        context("findAll - 전체 사용자 조회") {
            it("저장된 모든 사용자를 조회할 수 있다") {
                // when
                val users = userRepository.findAll()

                // then
                users shouldHaveSize 3 // 초기 데이터 3개
                users.map { it.id } shouldContain 1L
                users.map { it.id } shouldContain 2L
                users.map { it.id } shouldContain 3L
            }

            it("사용자를 추가하면 findAll 결과에 포함된다") {
                // given
                val now = LocalDateTime.now().format(dateFormatter)
                val newUser = User(
                    id = 200L,
                    balance = 30000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(newUser)

                // when
                val users = userRepository.findAll()

                // then
                users shouldHaveSize 4 // 초기 3개 + 추가 1개
                users.map { it.id } shouldContain 200L
            }
        }

        context("generateId - ID 생성") {
            it("호출할 때마다 순차적으로 증가하는 ID를 생성한다") {
                // when
                val id1 = userRepository.generateId()
                val id2 = userRepository.generateId()
                val id3 = userRepository.generateId()

                // then
                id1 shouldBe 4L // 초기값이 4L부터 시작
                id2 shouldBe 5L
                id3 shouldBe 6L
            }

            it("생성된 ID로 사용자를 저장할 수 있다") {
                // given
                val generatedId = userRepository.generateId()
                val now = LocalDateTime.now().format(dateFormatter)
                val newUser = User(
                    id = generatedId,
                    balance = 15000L,
                    createdAt = now,
                    updatedAt = now
                )

                // when
                userRepository.save(newUser)
                val foundUser = userRepository.findById(generatedId)

                // then
                foundUser shouldNotBe null
                foundUser!!.id shouldBe generatedId
                foundUser.balance shouldBe 15000L
            }
        }

        context("복합 시나리오") {
            it("사용자 생성 -> 조회 -> 수정 -> 재조회 시나리오가 정상 동작한다") {
                // 1. 사용자 생성
                val generatedId = userRepository.generateId()
                val now = LocalDateTime.now().format(dateFormatter)
                val newUser = User(
                    id = generatedId,
                    balance = 20000L,
                    createdAt = now,
                    updatedAt = now
                )
                userRepository.save(newUser)

                // 2. 조회
                val foundUser = userRepository.findById(generatedId)
                foundUser shouldNotBe null
                foundUser!!.balance shouldBe 20000L

                // 3. 수정 (잔액 충전)
                foundUser.balance = 70000L
                foundUser.updatedAt = LocalDateTime.now().format(dateFormatter)
                userRepository.save(foundUser)

                // 4. 재조회
                val updatedUser = userRepository.findById(generatedId)
                updatedUser shouldNotBe null
                updatedUser!!.balance shouldBe 70000L
            }
        }
    }
})