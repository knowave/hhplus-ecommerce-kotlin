package com.hhplus.ecommerce.domain.user

import com.hhplus.ecommerce.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, String>
