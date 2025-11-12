package com.hhplus.ecommerce.domain.payment.repository

import com.hhplus.ecommerce.domain.payment.entity.DataTransmission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DataTransmissionJpaRepository : JpaRepository<DataTransmission, UUID> {
    fun findByOrderId(orderId: UUID): DataTransmission?
}