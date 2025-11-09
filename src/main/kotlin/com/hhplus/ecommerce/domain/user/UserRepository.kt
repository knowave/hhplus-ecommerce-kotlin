package com.hhplus.ecommerce.domain.user

import com.hhplus.ecommerce.domain.user.entity.User

interface UserRepository {

    fun findById(userId: Long): User?

    fun save(user: User): User

    fun findAll(): List<User>

    fun generateId(): Long

    fun clear()
}