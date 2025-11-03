package com.hhplus.ecommerce.infrastructure.user

import com.hhplus.ecommerce.model.user.User

interface UserRepository {

    fun findById(userId: Long): User?

    fun save(user: User): User

    fun findAll(): List<User>

    fun generateId(): Long

    fun clear()
}