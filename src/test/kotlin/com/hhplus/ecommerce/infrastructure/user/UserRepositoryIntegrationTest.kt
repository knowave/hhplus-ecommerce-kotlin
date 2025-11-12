package com.hhplus.ecommerce.infrastructure.user

import com.hhplus.ecommerce.common.exception.UserNotFoundException
import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.repository.UserJpaRepository
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
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
class UserRepositoryIntegrationTest(
    private val userRepository: UserJpaRepository
) : DescribeSpec({
}) {
        override fun extensions(): List<Extension> = listOf(SpringExtension)

    private lateinit var userId: UUID

    init {
        beforeEach {
            val user = userRepository.save(User(balance = 100000L))
            userId = user.id!!
        }

        describe("UserRepository 통합 테스트") {
            context("findById - 사용자 조회") {
                it("존재하는 사용자 ID로 조회하면 사용자 정보를 반환한다") {
                    // when
                    val user = userRepository.findById(userId)
                        .orElseThrow { throw UserNotFoundException(userId) }

                    // then
                    user shouldNotBe null
                    user.id shouldBe userId
                    user.balance shouldBe 50000L
                }

                it("존재하지 않는 사용자 ID로 조회하면 null을 반환한다") {
                    // given
                    val nonExistentId = UUID.randomUUID()

                    // when
                    val user = userRepository.findById(nonExistentId)

                    // then
                    user shouldBe null
                }
            }

            context("save - 사용자 저장") {
                it("새로운 사용자를 저장할 수 있다") {
                    // given
                    val newUser = User(balance = 75000L)

                    // when
                    val savedUser = userRepository.save(newUser)

                    // then
                    savedUser.balance shouldBe 75000L
                }

                it("기존 사용자의 정보를 업데이트할 수 있다") {
                    // given
                    val existingUser = userRepository.findById(userId)
                        .orElseThrow { throw UserNotFoundException(userId) }

                    val updatedBalance = existingUser.balance + 10000L

                    existingUser.balance = updatedBalance

                    // when
                    val savedUser = userRepository.save(existingUser)

                    // then
                    savedUser.balance shouldBe updatedBalance
                }
            }
        }
    }
}