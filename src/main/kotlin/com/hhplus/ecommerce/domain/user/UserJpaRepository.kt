package com.hhplus.ecommerce.domain.user

import com.hhplus.ecommerce.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {
}