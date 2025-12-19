## 개발 진행
1. 프로젝트 최상위 경로에 위치한 docs와 docs/api를 기반으로 개발을 진행.
2. 변수 이름은 camelCase로 하고, class는 PascalCase로 한다.
3. 프로젝트 구조는 레이어드 아키텍처를 사용한다.
4. API 문서 자동화를 위해서 spring docs를 사용
5. 단일 책임 원칙과 의존성 역전 원칙을 반드시 지킨다.
6. Type은 절대 Any로 지정하지 않는다. 
7. GlobalCustomException을 사용하고 error message는 영어로 한다.
8. `application.yml` 파일에서 default path를 `/api` 로 설정되어 있어서 API 기본 경로에 `/api`를 붙이지 않아도 됨. 
9. 각 도메인의 pk는 `domainId`가 아니라 `id`로 정의.
10. JPA를 사용해서 MySQL DB를 사용함.
11. 각 테이블에 공통적으로 사용될 `id`, `createdAt`, `updatedAt`은 `BaseEntity`를 상속받아서 사용.
12. 조회가 자주 일어나는 테이블은 index를 사용하여 성능 개선.
13. InMemoryDB 에서 사용한 `Repository` class와 `RepositoryImpl` class는 사용하지않고 수정도 하지않는다.
14. Repository는 `JpaRepository`를 사용할것.
15. 동시성 제어는 Transaction을 사용
16. `서버구축 DB - 병목 분석 및 개선 방안.md` 파일을 확인하여 동시성제어 및 병목 제어
17. 가장 많이 주문한 상품 랭킹을 Redis 기반으로 개발하고 설계 구현
    * Redis의 `Sorted Set`을 사용.
      * 주문을 하면 실시간으로 랭킹에 적용이 됨.
    * 가장 많이 주문한 상품부터 내림차순으로 정렬
    * 주간 랭킹과 일간 랭킹 구현
    * 정렬 조건
      * 많이 주문한 순, 생성순
18. Redis 를 활용해 비동기로 동작하는 시스템으로 개선
    *. 기존 RDBMS 기반의 로직들을 Redis 기반의 로직으로 누락없이 마이그레이션

## Event Driven (W/Kafka)
1. Kafka를 통해서 이벤트 체이닝으로 아래 순서 보장.
  * 주문 -> 결제

## 테스트 코드
1. 테스트 코드는 `kotest`로 진행.
2. 비즈니스 로직을 테스트.
3. 테스트는 무조건 단위 테스트, 통합 테스트, e2e test를 진행.
4. `mockk`를 이용해서 mocking을 진행. 
5. 테스트 코드 커버리지는 70% 이상이어야 함. 
6. 테스트 코드를 확인하기 위해 `jacoco`를 사용.
7. 테스트 코드 70% 이상은 비즈니스 로직이 충족이 되었을 때 가능한 조건.
8. Repository는 `JpaRepository`를 사용할것.
9. 각 도메인의 service/repository 통합테스트, E2E 테스트는 JPA InMemoryDB를 사용할 것.
10. 각 도에인의 통합테스트와 E2E 테스트는 반드시 테스트용 데이터를 생성할 것.
11. 테스트 커버리지가 70% 이상이지만 각 테스트 케이스는 절대로 실패해서는 안된다.
12. unit test 동시성 제어가 필요한 테스트 케이스는 concurrentHashMap으로 처리.