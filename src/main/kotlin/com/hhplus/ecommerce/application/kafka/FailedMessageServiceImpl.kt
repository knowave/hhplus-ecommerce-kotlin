package com.hhplus.ecommerce.application.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.hhplus.ecommerce.application.kafka.dto.FailedMessageDto
import com.hhplus.ecommerce.application.kafka.dto.ReprocessResultDto
import com.hhplus.ecommerce.common.event.CouponIssuedEvent
import com.hhplus.ecommerce.common.event.OrderCreatedEvent
import com.hhplus.ecommerce.common.event.PaymentCompletedEvent
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import com.hhplus.ecommerce.domain.kafka.repository.FailedMessageJpaRepository
import com.hhplus.ecommerce.infrastructure.kafka.CouponEventProducer
import com.hhplus.ecommerce.infrastructure.kafka.OrderEventProducer
import com.hhplus.ecommerce.infrastructure.kafka.PaymentEventProducer
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * DLQ에 저장된 실패 메시지를 조회하고 수동으로 재처리
 */
@Service
class FailedMessageServiceImpl(
    private val failedMessageRepository: FailedMessageJpaRepository,
    private val orderEventProducer: OrderEventProducer?,
    private val paymentEventProducer: PaymentEventProducer?,
    private val couponEventProducer: CouponEventProducer?,
    private val objectMapper: ObjectMapper
) : FailedMessageService {

    @Transactional(readOnly = true)
    override fun getAllFailedMessages(): List<FailedMessageDto> {
        return failedMessageRepository.findAllByOrderByFailedAtDesc()
            .map { FailedMessageDto.from(it) }
    }

    @Transactional(readOnly = true)
    override fun getFailedMessagesByStatus(status: FailedMessageStatus): List<FailedMessageDto> {
        return failedMessageRepository.findByStatusOrderByFailedAtDesc(status)
            .map { FailedMessageDto.from(it) }
    }

    @Transactional(readOnly = true)
    override fun getFailedMessageById(id: UUID): FailedMessageDto {
        val failedMessage = failedMessageRepository.findByIdOrNull(id)
            ?: throw IllegalArgumentException("실패 메시지를 찾을 수 없습니다: id=$id")
        return FailedMessageDto.from(failedMessage)
    }

    @Transactional
    override fun reprocessFailedMessage(id: UUID): ReprocessResultDto {
        val failedMessage = failedMessageRepository.findByIdOrNull(id)
            ?: throw IllegalArgumentException("실패 메시지를 찾을 수 없습니다: id=$id")

        if (failedMessage.status == FailedMessageStatus.REPROCESSED) {
            return ReprocessResultDto(
                id = id,
                success = false,
                message = "이미 재처리된 메시지입니다"
            )
        }

        return try {
            when (failedMessage.topic) {
                OrderEventProducer.TOPIC_ORDER_CREATED -> {
                    reprocessOrderCreatedEvent(failedMessage.payload)
                }
                PaymentEventProducer.TOPIC_PAYMENT_COMPLETED -> {
                    reprocessPaymentCompletedEvent(failedMessage.payload)
                }
                CouponEventProducer.TOPIC_COUPON_ISSUED -> {
                    reprocessCouponIssuedEvent(failedMessage.payload)
                }
                else -> {
                    throw IllegalArgumentException("지원하지 않는 토픽입니다: ${failedMessage.topic}")
                }
            }

            failedMessage.markAsReprocessed("수동 재처리 성공")
            failedMessageRepository.save(failedMessage)

            ReprocessResultDto(
                id = id,
                success = true,
                message = "재처리가 성공적으로 완료되었습니다"
            )
        } catch (e: Exception) {
            ReprocessResultDto(
                id = id,
                success = false,
                message = "재처리 실패: ${e.message}"
            )
        }
    }

    @Transactional
    override fun ignoreFailedMessage(id: UUID, note: String) {
        val failedMessage = failedMessageRepository.findByIdOrNull(id)
            ?: throw IllegalArgumentException("실패 메시지를 찾을 수 없습니다: id=$id")

        failedMessage.markAsIgnored(note)
        failedMessageRepository.save(failedMessage)
    }

    @Transactional(readOnly = true)
    override fun getPendingCount(): Long {
        return failedMessageRepository.countByStatus(FailedMessageStatus.PENDING)
    }

    private fun reprocessOrderCreatedEvent(payload: String) {
        val event = objectMapper.readValue(payload, OrderCreatedEvent::class.java)
        orderEventProducer?.sendOrderCreatedEvent(event)
            ?: throw IllegalStateException("OrderEventProducer가 없습니다. Kafka가 비활성화되어 있습니다.")
    }

    private fun reprocessPaymentCompletedEvent(payload: String) {
        val event = objectMapper.readValue(payload, PaymentCompletedEvent::class.java)
        paymentEventProducer?.sendPaymentCompletedEvent(event)
            ?: throw IllegalStateException("PaymentEventProducer가 없습니다. Kafka가 비활성화되어 있습니다.")
    }

    private fun reprocessCouponIssuedEvent(payload: String) {
        val event = objectMapper.readValue(payload, CouponIssuedEvent::class.java)
        couponEventProducer?.sendCouponIssuedEvent(event)
            ?: throw IllegalStateException("CouponEventProducer가 없습니다. Kafka가 비활성화되어 있습니다.")
    }
}