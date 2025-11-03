package com.hhplus.ecommerce.infrastructure.user

import com.hhplus.ecommerce.model.user.User
import org.springframework.stereotype.Repository


@Repository
class UserRepositoryImpl : UserRepository {

    // ID 자동 생성을 위한 카운터
    private var nextId: Long = 4L

    // Mock 데이터 저장소 (추후 JPA로 대체)
    private val users: MutableMap<Long, User> = mutableMapOf(
        1L to User(
            id = 1L,
            balance = 50000L,
            createdAt = "2025-01-01T00:00:00",
            updatedAt = "2025-10-29T10:30:00"
        ),
        2L to User(
            id = 2L,
            balance = 100000L,
            createdAt = "2025-01-15T00:00:00",
            updatedAt = "2025-10-29T09:00:00"
        ),
        3L to User(
            id = 3L,
            balance = 25000L,
            createdAt = "2025-02-01T00:00:00",
            updatedAt = "2025-10-28T15:20:00"
        )
    )

    override fun findById(userId: Long): User? {
        return users[userId]
    }

    override fun save(user: User): User {
        users[user.id] = user
        return user
    }

    override fun findAll(): List<User> {
        return users.values.toList()
    }

    override fun generateId(): Long {
        return nextId++
    }

    override fun clear() {
        users.clear()
    }
}