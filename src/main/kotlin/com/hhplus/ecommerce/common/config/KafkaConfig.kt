package com.hhplus.ecommerce.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.util.backoff.FixedBackOff

/**
 * Producer와 Consumer의 Factory를 정의.
 * JSON 직렬화/역직렬화를 위한 ObjectMapper를 설정합.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(
    name = ["spring.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id}")
    private lateinit var groupId: String

    /**
     * Dead Letter Queue (DLQ) 토픽 네이밍 규칙
     * 원본 토픽 이름에 .DLQ suffix 추가
     */
    private val DLQ_SUFFIX = ".DLQ"

    /**
     * Jackson ObjectMapper 설정
     * Kotlin 및 JavaTime 모듈 지원
     */
    @Bean
    fun kafkaObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    /**
     * Producer Factory 설정
     * 메시지를 JSON 형식으로 직렬화하여 Kafka로 전송합니다.
     *
     * 순서 보장 설정:
     * - ACKS_CONFIG: "all" - 모든 레플리카가 메시지를 받았는지 확인
     * - ENABLE_IDEMPOTENCE_CONFIG: true - 멱등성 보장 (중복 메시지 방지)
     * - MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION: 1 - 순서 보장을 위해 한 번에 하나의 요청만 처리
     */
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",  // 모든 레플리카 확인
            ProducerConfig.RETRIES_CONFIG to 3,  // 재시도 횟수
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,  // 멱등성 보장
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1  // 순서 보장 (동일 파티션 내)
        )
        return DefaultKafkaProducerFactory(configProps, StringSerializer(), JsonSerializer(kafkaObjectMapper()))
    }

    /**
     * KafkaTemplate Bean
     * Producer를 사용하여 메시지를 전송하는 템플릿
     */
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }

    /**
     * Consumer Factory 설정
     * Kafka에서 메시지를 수신하여 JSON 역직렬화.
     */
    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",  // 가장 오래된 메시지부터
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false  // 수동 커밋
        )

        val deserializer = JsonDeserializer(Any::class.java, kafkaObjectMapper())
        deserializer.addTrustedPackages("*")

        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            deserializer
        )
    }

    /**
     * Default Error Handler 설정
     * Consumer에서 예외 발생 시 재시도 로직 및 DLQ 전송 처리
     *
     * - 재시도 3회 (FixedBackOff: 3초 간격)
     * - 재시도 실패 시 DeadLetterPublishingRecoverer를 통해 DLQ 토픽으로 전송
     * - DLQ 토픽 이름: {원본토픽}.DLQ (예: order-created.DLQ)
     */
    @Bean
    fun defaultErrorHandler(): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate()) { record, _ ->
            // DLQ 토픽 이름 생성: 원본 토픽 + .DLQ
            org.apache.kafka.common.TopicPartition(record.topic() + DLQ_SUFFIX, record.partition())
        }

        // FixedBackOff: 3초 간격으로 3회 재시도 (총 4회 시도)
        return DefaultErrorHandler(recoverer, FixedBackOff(3000L, 3L))
    }

    /**
     * Kafka Listener Container Factory
     * Consumer를 위한 컨테이너 팩토리 설정
     *
     * DefaultErrorHandler가 자동으로 offset 커밋 및 예외 처리를 수행:
     * - 메시지 처리 성공 시: 자동으로 offset 커밋
     * - 메시지 처리 실패 시: 재시도 3회 수행
     * - 재시도 모두 실패 시: DLQ 토픽으로 전송
     */
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setCommonErrorHandler(defaultErrorHandler())  // Error Handler 설정
        return factory
    }

    /**
     * DLQ 전용 Consumer Factory 설정
     * DLQ 메시지를 String으로 역직렬화하여 DB에 저장
     */
    @Bean
    fun dlqConsumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "$groupId-dlq",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false
        )

        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            StringDeserializer()
        )
    }

    /**
     * DLQ 전용 Kafka Listener Container Factory
     * String으로 역직렬화된 메시지를 처리
     */
    @Bean
    fun dlqKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = dlqConsumerFactory()
        return factory
    }
}