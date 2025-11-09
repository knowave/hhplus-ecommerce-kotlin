package com.hhplus.ecommerce.domain.payment

import com.hhplus.ecommerce.domain.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentJpaRepository : JpaRepository<Payment, UUID> {
}