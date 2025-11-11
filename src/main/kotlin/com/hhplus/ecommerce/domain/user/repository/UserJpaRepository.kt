package com.hhplus.ecommerce.domain.user.repository

import com.hhplus.ecommerce.domain.user.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {

    /**
     * 비관적 락을 사용하여 사용자를 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용합니다:
     * - 잔액 차감/환불 시 사용
     * - 트랜잭션 종료 시까지 다른 트랜잭션이 해당 행을 수정할 수 없음
     *
     * @param id 사용자 ID
     * @return 잠금이 걸린 사용자 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    fun findByIdWithLock(@Param("id") id: UUID): Optional<User>
}