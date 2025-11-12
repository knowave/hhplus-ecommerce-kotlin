# ==============================
# 🏗️ Build stage
# ==============================
FROM bellsoft/liberica-openjdk-alpine:17 AS builder

WORKDIR /app

# Gradle wrapper 실행 권한 부여
COPY gradlew .
RUN chmod +x gradlew

# Gradle 설정 파일 먼저 복사 (캐시 최적화)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# 의존성 미리 다운로드 (build cache 최적화)
RUN ./gradlew dependencies || true

# 나머지 소스 복사
COPY . .

# 테스트 제외하고 빌드
RUN ./gradlew clean build -x test


# ==============================
# 🚀 Run stage
# ==============================
FROM bellsoft/liberica-openjdk-alpine:17

WORKDIR /app

# 빌드 결과물만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 환경 변수 설정 (필요시)
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 포트 노출
EXPOSE 8080

# Spring Boot 실행 명령어
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]