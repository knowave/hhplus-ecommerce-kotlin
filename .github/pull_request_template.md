## :pushpin: PR 제목 규칙

[STEP0X] 이름

---

### **핵심 체크리스트** :white_check_mark:

#### :one: API 설계

- [ ] 요구 사항에 맞게 설계를 했는가?
- [ ] 요구 사항에 대한 flow chart를 작성했는가?

#### :two: DB diagram

- [ ] 데이터의 관계성을 잘 보여주는가?
- [ ] 각 DB Table에 대한 정의는 잘 되어있는가?

#### :three: sequence diagram

- [ ] 비즈니스 정책을 잘 잡았는가?

#### :four: Mock Server

- [ ] swagger/openAPI 문서를 작성했는가?
- [ ] 설계한 API 대로 req/res body를 잘 작성했는가? 

#### :five: AI 활용

- [ ] Claude Code를 활용하여 개발했는가?
- [ ] Custom Commands나 프롬프트 최적화를 시도했는가?

---

### **리뷰 포인트(질문)**

- 주문 시 결제를 같이 진행할지 혹은 주문 성공 시 결제 API를 요청해서 따로 처리할지 고민이 됩니다.
- History table을 최대한 구현하지 않을 생각인데, 확장성을 고려할 때 꼭 필요할지 궁금합니다.