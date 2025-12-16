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
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

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
     * Kafka Listener Container Factory
     * Consumer를 위한 컨테이너 팩토리 설정
     * 수동 ACK 모드를 사용하여 메시지 처리 성공 시에만 offset을 커밋.
     */
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL  // 수동 ACK
        return factory
    }
}