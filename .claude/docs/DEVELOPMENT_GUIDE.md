## 개발 진행
1. 프로젝트 최상위 경로에 위치한 docs와 docs/api를 기반으로 개발을 진행.
2. 변수 이름은 camelCase로 하고, class는 PascalCase로 한다.
3. 실제 비즈니스 로직은 src/.../domains로 각 domain을 만들어서 진행한다.
4. API 문서 자동화를 위해서 spring docs를 사용
5. 개발 단계가 아니기에 Mock Server를 docs/api를 이용하여 구현한다. 
6. Mock Server를 구현할 때 controller class 에서 간단하게 Map or Object를 사용해서 구현한다.
7. Repository (임시 Mock으로 대체), Service, Controller 계층을 분리한다.
8. Type은 절대로 Any로 지정하지 않는다.
9. GlobalCustomException을 사용하고 error message는 영어로 한다.
10. `application.yml` 파일에서 default path를 `/api` 로 설정되어 있어서 API 기본 경로에 `/api`를 붙이지 않아도 됨.
11. 각 도메인의 pk는 `domainId`가 아니라 `id`로 정의.