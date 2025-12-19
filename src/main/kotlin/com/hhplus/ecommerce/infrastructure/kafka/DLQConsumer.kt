package com.hhplus.ecommerce.infrastructure.kafka

import com.hhplus.ecommerce.domain.kafka.entity.FailedMessage
import com.hhplus.ecommerce.domain.kafka.entity.FailedMessageStatus
import com.hhplus.ecommerce.domain.kafka.repository.FailedMessageJpaRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Dead Letter Queue (DLQ) Consumer
 *
 * 재시도 3회 실패 후 DLQ 토픽으로 전송된 메시지를 소비하여
 * 데이터베이스에 저장합니다. 관리자가 나중에 수동으로 확인하고
 * 재처리할 수 있도록 합니다.
 *
 * DLQ 토픽 패턴:
 * - order-created.DLQ
 * - payment-completed.DLQ
 */
@Component
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class DLQConsumer(
    private val failedMessageRepository: FailedMessageJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 생성 이벤트 DLQ 메시지 소비
     */
    @KafkaListener(
        topics = ["order-created.DLQ"],
        groupId = "\${spring.kafka.consumer.group-id}-dlq",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    fun consumeOrderCreatedDLQ(
        record: ConsumerRecord<String, String>,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) errorMessage: String?,
        @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) stackTrace: String?
    ) {
        logger.warn(
            "DLQ Consumer - 실패한 메시지 수신: topic={}, partition={}, offset={}, key={}",
            record.topic(),
            partition,
            offset,
            record.key()
        )

        saveFailedMessage(record, partition, offset, errorMessage, stackTrace)
    }

    /**
     * 결제 완료 이벤트 DLQ 메시지 소비
     */
    @KafkaListener(
        topics = ["payment-completed.DLQ"],
        groupId = "\${spring.kafka.consumer.group-id}-dlq",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    fun consumePaymentCompletedDLQ(
        record: ConsumerRecord<String, String>,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) errorMessage: String?,
        @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) stackTrace: String?
    ) {
        logger.warn(
            "DLQ Consumer - 실패한 메시지 수신: topic={}, partition={}, offset={}, key={}",
            record.topic(),
            partition,
            offset,
            record.key()
        )

        saveFailedMessage(record, partition, offset, errorMessage, stackTrace)
    }

    /**
     * 실패한 메시지를 데이터베이스에 저장
     *
     * @param record Kafka 레코드
     * @param partition 파티션 번호
     * @param offset Offset 위치
     * @param errorMessage 에러 메시지
     * @param stackTrace 스택 트레이스
     */
    private fun saveFailedMessage(
        record: ConsumerRecord<String, String>,
        partition: Int,
        offset: Long,
        errorMessage: String?,
        stackTrace: String?
    ) {
        try {
            // DLQ 토픽 이름에서 원본 토픽 이름 추출 (예: order-created.DLQ -> order-created)
            val originalTopic = record.topic().removeSuffix(".DLQ")

            val failedMessage = FailedMessage(
                topic = originalTopic,
                partition = partition,
                offset = offset,
                messageKey = record.key(),
                payload = record.value(),
                errorMessage = errorMessage ?: "Unknown error",
                stackTrace = stackTrace,
                failedAt = LocalDateTime.now(),
                retryCount = 3,  // ErrorHandler에서 3회 재시도 후 DLQ로 전송
                status = FailedMessageStatus.PENDING
            )

            failedMessageRepository.save(failedMessage)

            logger.info(
                "DLQ Consumer - 실패 메시지 저장 완료: topic={}, partition={}, offset={}, failedMessageId={}",
                originalTopic,
                partition,
                offset,
                failedMessage.id
            )
        } catch (e: Exception) {
            logger.error(
                "DLQ Consumer - 실패 메시지 저장 실패: topic={}, partition={}, offset={}, error={}",
                record.topic(),
                partition,
                offset,
                e.message,
                e
            )
            // DLQ 메시지 저장에 실패하면 메시지가 다시 소비되지 않도록 ACK는 처리됨
            // 대신 로그에 에러를 기록하여 관리자가 확인할 수 있도록 함
        }
    }

    /**
     * Exception을 스택 트레이스 문자열로 변환
     */
    private fun getStackTraceAsString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}