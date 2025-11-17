package com.hhplus.ecommerce.domain.user.repository

import com.hhplus.ecommerce.domain.user.entity.User
import java.util.UUID

interface UserRepository {

    fun findById(userId: UUID): User?

    fun save(user: User): User

    fun findAll(): List<User>

    fun clear()
}