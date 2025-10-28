# 이커머스 프로젝트 문서

이 디렉토리는 이커머스 백엔드 API 시스템의 설계 및 개발 문서를 포함합니다.

## 📁 문서 구조

```
docs/
├── README.md                    # 이 파일 (문서 가이드)
├── DATABASE_SCHEMA.md           # 데이터베이스 스키마 및 ERD
└── api/                         # API 설계 문서
    ├── requirements.md          # 비즈니스 요구사항
    ├── user-stories.md          # 사용자 스토리
    ├── api-specification.md     # API 명세서
    └── data-models.md           # 데이터 모델 정의
```

---

## 📚 문서별 역할

### 1. [requirements.md](./api/requirements.md)
비즈니스 요구사항을 정의하는 문서

**포함 내용**:
- 기능적 요구사항 (Functional Requirements)
- 비기능적 요구사항 (Non-Functional Requirements)
- 사용자 역할 및 권한
- 비즈니스 규칙 및 제약사항
- 기술 스택 및 우선순위

**사용 시점**:
- 프로젝트 시작 시 요구사항 정의
- 스테이크홀더와의 커뮤니케이션
- 개발 범위 확인

---

### 2. [user-stories.md](./api/user-stories.md)
사용자 관점의 기능 명세

**포함 내용**:
- Epic 단위 기능 그룹화
- "As a / I want / So that" 형식의 사용자 스토리
- Acceptance Criteria (인수 조건)
- 예제 시나리오 및 실패 케이스
- 동시성 테스트 시나리오

**사용 시점**:
- 기능 개발 전 요구사항 명확화
- 테스트 케이스 작성
- 클라이언트와의 소통

---

### 3. [api-specification.md](./api/api-specification.md)
기술적 API 명세서

**포함 내용**:
- 엔드포인트 및 HTTP 메서드
- 요청/응답 데이터 구조
- HTTP 상태 코드
- 에러 코드 및 메시지
- 비즈니스 규칙 요약

**사용 시점**:
- API 개발 시 참고
- 프론트엔드 개발자와의 협업
- API 문서 자동화 (Swagger/OpenAPI)

---

### 4. [data-models.md](./api/data-models.md)
비즈니스 도메인 모델 정의

**포함 내용**:
- 엔티티별 핵심 속성 및 메서드
- 비즈니스 규칙
- 엔티티 관계
- 금액 계산 공식
- 상태 전이 다이어그램

**사용 시점**:
- 도메인 모델 설계
- DB 설계와 비즈니스 로직 매핑
- 코드 구현 시 참고

---

### 5. [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md)
데이터베이스 스키마 상세 문서

**포함 내용**:
- ERD (Entity Relationship Diagram)
- 테이블별 컬럼 정의
- 인덱스 및 제약조건
- 동시성 제어 전략
- Mermaid 다이어그램

**사용 시점**:
- DB 설계 및 마이그레이션
- 성능 최적화
- 데이터베이스 이해

---

## 🔄 개발 워크플로우

### Phase 1: 요구사항 분석
1. `requirements.md` 작성
   - 비즈니스 요구사항 정의
   - 기능적/비기능적 요구사항 구분

### Phase 2: 사용자 스토리 작성
2. `user-stories.md` 작성
   - 사용자 관점의 기능 명세
   - Acceptance Criteria 정의

### Phase 3: API 설계
3. `api-specification.md` 작성
   - 엔드포인트 설계
   - 요청/응답 구조 정의

4. `data-models.md` 작성
   - 도메인 모델 설계
   - 비즈니스 규칙 정의

5. `DATABASE_SCHEMA.md` 작성
   - 데이터베이스 스키마 설계
   - ERD 작성

### Phase 4: 코드 구현
6. Entity 클래스 구현
7. Repository 구현
8. Service 로직 구현
9. Controller 구현

### Phase 5: 테스트
10. 단위 테스트 작성
11. 통합 테스트 작성
12. 동시성 테스트 작성

---

## 📖 관련 문서

### 프로젝트 내부 문서
- [비즈니스 정책 문서](../.claude/docs/BUSINESS_POLICIES.md)
- [개발 가이드](../.claude/docs/DEVELOPMENT_GUIDE.md)
- [프로젝트 가이드](../CLAUDE.md)

### 외부 문서
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Kotlin 공식 문서](https://kotlinlang.org/docs/home.html)
- [REST API 설계 가이드](https://restfulapi.net/)

---

## 🛠️ 문서 작성 가이드

### Claude Code 활용 프롬프트

#### 1. 요구사항 문서 생성
```
이커머스 시스템의 요구사항을 분석해서 docs/api/requirements.md 파일을 작성해주세요.
기능적 요구사항과 비기능적 요구사항을 구분하고,
사용자 역할과 권한도 포함해주세요.
```

#### 2. 사용자 스토리 생성
```
requirements.md를 기반으로 user-stories.md를 작성해주세요.
As a [role], I want [feature], so that [benefit] 형식으로 작성하고,
각 스토리마다 인수 조건(Acceptance Criteria)도 포함해주세요.
```

#### 3. API 명세서 생성
```
user-stories.md를 기반으로 RESTful API 명세서를 작성해주세요.
엔드포인트, HTTP 메소드, 요청/응답 형식을 포함하고,
에러 처리와 상태 코드도 정의해주세요.
```

#### 4. 데이터 모델 설계
```
API 명세서를 기반으로 데이터 모델을 설계해주세요.
Entity Relationship Diagram과 각 엔티티의 속성,
제약조건, 인덱스 정보를 포함해주세요.
```

---

## ✅ 문서 품질 체크리스트

### requirements.md
- [ ] 기능적 요구사항이 명확하게 정의되었는가?
- [ ] 비기능적 요구사항(성능, 보안 등)이 포함되었는가?
- [ ] 제외 사항(Out of Scope)이 명시되었는가?
- [ ] 우선순위가 정의되었는가?

### user-stories.md
- [ ] 모든 스토리가 "As a / I want / So that" 형식인가?
- [ ] Acceptance Criteria가 명확한가?
- [ ] 예제 시나리오가 포함되었는가?
- [ ] 실패 케이스가 정의되었는가?
- [ ] 동시성 테스트 시나리오가 포함되었는가?

### api-specification.md
- [ ] 모든 엔드포인트가 문서화되었는가?
- [ ] 요청/응답 형식이 명확한가?
- [ ] HTTP 상태 코드가 적절한가?
- [ ] 에러 코드와 메시지가 정의되었는가?
- [ ] 비즈니스 규칙이 요약되었는가?

### data-models.md
- [ ] 모든 엔티티가 정의되었는가?
- [ ] 엔티티 관계가 명확한가?
- [ ] 비즈니스 규칙이 포함되었는가?
- [ ] 상태 전이가 정의되었는가?

### DATABASE_SCHEMA.md
- [ ] ERD가 포함되었는가?
- [ ] 테이블 구조가 상세하게 정의되었는가?
- [ ] 인덱스와 제약조건이 명시되었는가?
- [ ] 동시성 제어 전략이 설명되었는가?

---

## 🔧 문서 유지보수

### 문서 업데이트 시점
- 새로운 기능 추가 시
- API 변경 시
- 비즈니스 규칙 변경 시
- 버그 수정으로 인한 로직 변경 시

### 문서 동기화 체크
- 코드 변경 시 관련 문서도 함께 업데이트
- PR(Pull Request) 시 문서 변경 사항 포함
- 정기적인 문서 리뷰 (월 1회 권장)

---

## 📝 문서 작성 원칙

1. **명확성**: 모호한 표현 지양, 구체적인 예시 포함
2. **일관성**: 용어 및 형식 통일
3. **최신성**: 코드와 문서의 동기화 유지
4. **접근성**: 누구나 이해할 수 있는 설명
5. **간결성**: 핵심 내용 위주로 작성

---

## 🤝 기여 가이드

### 문서 수정 시
1. 관련 문서를 모두 확인
2. 변경 사항이 다른 문서에 영향을 미치는지 검토
3. 예제 및 시나리오 업데이트
4. 팀원에게 리뷰 요청

### 새 문서 추가 시
1. 이 README에 새 문서 정보 추가
2. 다른 문서와의 연관 관계 명시
3. 목적과 사용 시점 명확히 기술

---

## 📞 문의

문서 관련 질문이나 개선 제안은 팀 채널을 통해 공유해주세요.