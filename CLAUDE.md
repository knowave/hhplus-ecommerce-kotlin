# CLAUDE.md

Claude Code (claude.ai/code) 작업을 위한 프로젝트 가이드입니다.

## 📋 필수 참고 파일 (순서대로)
모든 상세 문서는 `.claude/docs/` 폴더에 위치합니다:

1. **DEVELOPMENT_GUIDE.md** - 개발 진행 순서 및 커맨드 사용법
2. **BUSINESS_POLICIES.md** - 구현해야 할 비즈니스 정책 정의
3. **PROJECT_GUIDELINES.md** - 과제 요구사항 및 평가 기준

## 시작하기
**첫 작업 시 반드시 확인**: `.claude/docs/DEVELOPMENT_GUIDE.md`의 페이즈별 진행 순서를 따라서 개발.

## 요구사항
질문과 답변은 무조건 한국어로 진행.

## Project Overview

This is an e-commerce application built with Kotlin and Spring Boot. The project uses:
- Kotlin 1.9.25
- Spring Boot 3.5.6
- JPA/Hibernate for data persistence
- MySQL database
- JWT for authentication

## Build and Development Commands

### Building and Running
```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.hhplus.ecommerce.ClassName"

# Run a specific test method
./gradlew test --tests "com.hhplus.ecommerce.ClassName.methodName"

# Run tests with verbose output
./gradlew test --info
```

## Environment Configuration

The application uses a `.env` file for environment variables, loaded via the dotenv-kotlin library. Required environment variables:

- `DB_HOST` - Database host
- `DB_PORT` - Database port
- `DB_NAME` - Database name
- `DB_USER` - Database username
- `DB_PASSWORD` - Database password
- `JWT_SECRET` - JWT secret key

Environment variables are loaded in `EcommerceApplication.kt` main function before Spring Boot starts.

## Architecture Notes

### Package Structure
The codebase follows a standard Spring Boot Kotlin structure:
- `src/main/kotlin/com/hhplus/ecommerce/` - Main application code
- `src/test/kotlin/com/hhplus/ecommerce/` - Test code
- `src/main/resources/` - Configuration files

### JPA Configuration
The project uses Kotlin JPA plugin with specific configurations in `build.gradle.kts`:
- `allOpen` plugin ensures JPA entities are open for inheritance
- `noArg` plugin generates no-arg constructors for entities
- Annotate entity classes with `@Entity`, `@MappedSuperclass`, or `@Embeddable`

### Database
- Uses MySQL with JPA/Hibernate
- DDL auto mode is set to `update` (schema auto-updated on startup)
- SQL logging is enabled with formatting and highlighting in development

## Technology Stack Notes

### Kotlin-Specific
- Compiler uses strict JSR-305 annotations (`-Xjsr305=strict`)
- Requires `kotlin-reflect` for reflection support
- Uses `jackson-module-kotlin` for JSON serialization

### Spring Boot
- Web MVC for REST APIs
- Spring Data JPA for database access
- Context path is `/` on port 8080
- UTF-8 encoding enforced throughout

## Testing
- Uses JUnit 5 (Jupiter) as test framework
- Spring Boot Test for integration testing
- Test runtime uses JUnit Platform Launcher