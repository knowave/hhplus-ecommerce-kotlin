package com.hhplus.ecommerce.domain.transmission

import com.hhplus.ecommerce.domain.transmission.entity.DataTransmission
import com.hhplus.ecommerce.domain.transmission.entity.TransmissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DataTransmissionRepository : JpaRepository<DataTransmission, String> {

    fun findByOrderId(orderId: String): DataTransmission?

    fun findByStatus(status: TransmissionStatus): List<DataTransmission>

    @Query("SELECT dt FROM DataTransmission dt WHERE dt.status IN (:statuses) ORDER BY dt.createdAt ASC")
    fun findByStatusInOrderByCreatedAtAsc(statuses: List<TransmissionStatus>): List<DataTransmission>

    @Query("SELECT dt FROM DataTransmission dt WHERE dt.status = :status AND dt.attempts < :maxAttempts")
    fun findRetryableTransmissions(status: TransmissionStatus, maxAttempts: Int): List<DataTransmission>
}
