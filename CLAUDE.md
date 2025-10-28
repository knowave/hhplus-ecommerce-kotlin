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
- 질문과 답변은 무조건 한국어로 진행.
- 단계별로 진행을 하고 각 단계별로 왜 이렇게 구현을 했는지 상세하게 설명을 한다.
- 예시는 예시일뿐 그대로 하지 않고 요구분석 문서를 참고한다.

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