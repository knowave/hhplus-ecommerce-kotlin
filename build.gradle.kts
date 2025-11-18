plugins {
    val kotlinVersion = "1.9.25"

    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version kotlinVersion
    jacoco
}

group = "com.hhplus"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.5.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")

    // Redis & Cache
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")

    // MockK
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // Embedded Redis for testing
    testImplementation("it.ozimov:embedded-redis:0.7.3") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

noArg {
    annotation("jakarta.persistence.Entity")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // test 실행 후 자동으로 리포트 생성
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // test가 먼저 실행되도록 설정

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/EcommerceApplication**",  // main 메서드 제외
                    "**/dto/**",  // DTO 클래스 제외 (선택사항)
                    "**/config/**"  // Configuration 클래스 제외 (선택사항)
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    violationRules {
        rule {
            enabled = true
            element = "CLASS"

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()  // 70% 이상 요구
            }

            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()  // 분기 커버리지 70% 이상
            }
        }
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/EcommerceApplication**",
                    "**/dto/**",
                    "**/config/**"
                )
            }
        })
    )
}