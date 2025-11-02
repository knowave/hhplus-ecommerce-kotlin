package com.hhplus.ecommerce.domains.user

import com.hhplus.ecommerce.domains.user.dto.User

interface UserRepository {

    fun findById(userId: Long): User?

    fun save(user: User): User

    fun findAll(): List<User>

    fun generateId(): Long
}