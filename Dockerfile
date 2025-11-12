# ==============================
# 🏗️ Build stage
# ==============================
FROM bellsoft/liberica-openjdk-alpine:17 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Gradle wrapper 실행 권한 부여
COPY gradlew .
RUN chmod +x gradlew

# Gradle 설정 파일 먼저 복사 (의존성 캐시 최적화)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Gradle 캐시 디렉토리 생성
RUN mkdir -p /root/.gradle

# 의존성만 미리 다운로드 (캐시 최적화)
RUN ./gradlew build -x test --dry-run

# 나머지 소스 코드 복사
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

# 환경 변수 (외부에서 쉽게 override 가능)
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 포트 노출
EXPOSE 8080

# 안전한 exec-form ENTRYPOINT 사용
ENTRYPOINT ["java", "-jar", "app.jar"]

# 기본 JVM 옵션 전달
CMD ["-Xms256m", "-Xmx512m"]