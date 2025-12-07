package com.hhplus.ecommerce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import io.github.cdimascio.dotenv.dotenv
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
class EcommerceApplication

fun main(args: Array<String>) {
    val env = System.getProperty("spring.profiles.active") ?: System.getenv("SPRING_PROFILES_ACTIVE") ?: "default"

    if (env != "prod") {
        val dotenv = dotenv()

        dotenv.entries().forEach { entry ->
            System.setProperty(entry.key, entry.value)
        }
    }

	runApplication<EcommerceApplication>(*args)
}
