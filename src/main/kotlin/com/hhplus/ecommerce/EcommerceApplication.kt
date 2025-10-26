package com.hhplus.ecommerce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import io.github.cdimascio.dotenv.dotenv

@SpringBootApplication
@EnableJpaAuditing
class EcommerceApplication

fun main(args: Array<String>) {
	val dotenv = dotenv()

	dotenv.entries().forEach { entry ->
		System.setProperty(entry.key, entry.value)
	}

	runApplication<EcommerceApplication>(*args)
}
