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
10. 실제 DB를 사용하지 않고 인메모리 DB를 사용함.

## 테스트 코드
1. 테스트 코드는 `kotest`로 진행.
2. 비즈니스 로직을 테스트.
3. 테스트는 무조건 단위 테스트, 통합 테스트, e2e test를 진행.
4. `mockk`를 이용해서 mocking을 진행. 
5. 테스트 코드 커버리지는 70% 이상이어야 함. 
6. 테스트 코드를 확인하기 위해 `jacoco`를 사용.
7. 테스트 코드 70% 이상은 비즈니스 로직이 충족이 되었을 때 가능한 조건.