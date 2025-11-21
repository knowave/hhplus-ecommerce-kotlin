package com.hhplus.ecommerce.domain.product.repository

import com.hhplus.ecommerce.domain.product.entity.Product
import com.hhplus.ecommerce.domain.product.entity.ProductCategory
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface ProductJpaRepository : JpaRepository<Product, UUID> {
    @Query("""
        SELECT p FROM Product p
        WHERE (:category IS NULL OR p.category = :category)
    """)
    fun findAllWithFilter(
        @Param("category") category: ProductCategory?,
        pageable: Pageable
    ): Page<Product>

    /**
     * ✅ DB 정렬 최적화: 인기 상품 조회
     * 
     * salesCount > 0인 상품을 판매량 기준으로 정렬하여 반환합니다.
     * 메모리에서 전체 상품을 정렬하는 것보다 DB에서 정렬하고 필요한 개수만 반환합니다.
     * 
     * 정렬 기준:
     * 1. salesCount DESC (판매량)
     * 2. (price * salesCount) DESC (매출액)
     * 3. id ASC (안정적 정렬)
     */
    @Query("""
        SELECT p FROM Product p
        WHERE p.salesCount > 0
        ORDER BY p.salesCount DESC, (p.price * p.salesCount) DESC, p.id ASC
    """)
    fun findTopProducts(pageable: Pageable): List<Product>

    /**
     * 비관적 락을 사용하여 상품을 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용합니다:
     * - 재고 차감/복원 시 사용
     * - 트랜잭션 종료 시까지 다른 트랜잭션이 해당 행을 수정할 수 없음
     *
     * @param id 상품 ID
     * @return 잠금이 걸린 상품 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithLock(@Param("id") id: UUID): Optional<Product>

    /**
     * 비관적 락을 사용하여 여러 상품을 조회합니다.
     *
     * 동시성 제어가 필요한 경우 사용합니다:
     * - 주문 시 여러 상품의 재고를 동시에 차감할 때 사용
     * - 데드락 방지를 위해 ID를 정렬하여 조회할 것을 권장
     *
     * @param ids 상품 ID 목록
     * @return 잠금이 걸린 상품 엔티티 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    fun findAllByIdWithLock(@Param("ids") ids: List<UUID>): List<Product>
}