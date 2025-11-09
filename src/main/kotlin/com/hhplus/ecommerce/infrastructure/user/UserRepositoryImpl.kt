package com.hhplus.ecommerce.infrastructure.user

import com.hhplus.ecommerce.domain.user.entity.User
import com.hhplus.ecommerce.domain.user.UserRepository
import org.springframework.stereotype.Repository
import java.util.UUID


@Repository
class UserRepositoryImpl : UserRepository {

    // ID 자동 생성을 위한 카운터
    private var nextId: Long = 4L

    // Mock 데이터 저장소 (추후 JPA로 대체)
    private val users: MutableMap<UUID, User> = mutableMapOf()

    init {
        listOf<User>(
            User(
                balance = 50000L,
            ),
            User(
                balance = 100000L,
            ),
            User(
                balance = 25000L,
            )
        )
    }

    private fun assignId(user: User) {
        if (user.id == null) {
            val idField = user.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(user, UUID.randomUUID())
        }
    }

    override fun findById(userId: UUID): User? {
        return users[userId]
    }

    override fun save(user: User): User {
        assignId(user)
        users[user.id!!] = user
        return user
    }

    override fun findAll(): List<User> {
        return users.values.toList()
    }

    override fun clear() {
        users.clear()
    }
}