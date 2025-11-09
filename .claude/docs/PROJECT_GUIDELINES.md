## 프로젝트 가이드
* requirements-analysis.MD 파일은 요구사항 분석

### 프로젝트 요구 사항
레이어드 아키텍처로 프로젝트 구조를 맞춤
• 4계층(Presentation, Application, Domain, Infrastructure)이 명확히 분리
• 도메인 모델이 비즈니스 규칙을 포함이 되어야 함?
• Repository 패턴이 적용되어 인터페이스와 구현체가 분리
• 핵심 비즈니스 로직(재고/주문/쿠폰)이 정상 동작
• 단위 테스트 커버리지가 70% 이상
• DB 없이 인메모리로 구현