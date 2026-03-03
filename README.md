# spring-gift-refactoring

## 기능 요구 사항
이번 단계의 목표는 작동을 바꾸기 쉬운 상태를 만드는 것이다.
즉, 구조 변경을 통해 변경 난이도를 낮추되 작동은 유지한다.

- [x] 스타일 정리
  - 프로젝트 전반의 스타일 불일치를 찾아 일관되게 정리한다.
  - 스타일 정리로 인해 작동이 바뀌지 않아야 한다.
- [x] 불필요한 코드 제거(작동 변경 없음
  - IDE 또는 정적 분석 도구가 "미사용"으로 표시하는 항목을 제거할 수 있다.
  - 단, 삭제 전에 반드시 근거를 확인한다.
    - 주변 주석 또는 TODO에 의도가 있는가
    - git blame으로 누가 왜 추가했는가
    - 이후 단계(작동 변경)와 충돌하지 않는가
- [x] 서비스 계층 추출(구조 변경, 작동 변경 없음)
  - Controller의 비즈니스 로직을 Service로 이동한다.
  - Controller는 요청 검증과 위임만 담당하도록 얇게 만든다.
  - 이 단계에서는 신규 기능을 추가하지 않는다.
- [x] 테스트 코드 작성
  - 단위 테스트 작성
  - 인수 테스트 작성

## 프로그래밍 요구 사항
- 코드를 포기하지 않는다.
    AI를 쓰더라도 "행동만 맞으면 된다"로 끝내지 않는다.
    코드 품질, 복잡도, 테스트와 커버리지를 계속 확인한다.
    목표는 "AI가 다 해줌"이 아니라 "내가 더 중요한 결정을 더 많이 하는 것"이다.
- 계획 파일을 기준점으로 삼는다.
    작업은 지금 할 다음 한 가지가 명확해야 한다.
    plan 또는 README.md 체크리스트에 다음 작업이 적혀 있어야 코드 수정을 시작한다.
- TDD 루프를 유지한다.
    가능하면 Red, Green, Refactor 순서로 진행한다.
    최소 요구 사항은 "변경 후 전체 테스트 통과"다.
- 구조 변경과 작동 변경을 섞지 않는다.
    구조 변경은 구조만, 작동 변경은 작동만 다룬다.
    한 커밋에는 둘 중 하나만 담는다.
- AI가 앞서 달리면 즉시 멈춘다.
    반복과 복잡도가 늘어나거나, 요청하지 않은 작동을 추가하려 하면 즉시 중단한다.
    테스트를 회피하거나 비활성화하려는 흔적이 보이면 즉시 되돌린다.
- 한 번에 한 조각만 바꾼다.
    "다음 변경 1개"처럼 범위를 제한한다.
    AI에게도 "지금은 이것만"을 명확히 지시한다.
- 커밋은 논리 단위, 설명 가능한 diff로만 한다.
    커밋은 목적 1개로 구성한다.
    git diff를 보고 커밋 의도를 30초 안에 설명할 수 없으면 더 쪼갠다.
- AI 산출물을 그대로 믿지 않는다.
    AI는 초안을 만들 뿐, 설계와 검증 책임은 개발자에게 남는다.
    중간 결과를 자주 확인하고, 의도하지 않은 변경이 없으면 즉시 제거한다.

## 테스트 실행 방법

### 전체 테스트 실행
```bash
./gradlew test
```

### 단위테스트만 실행
```bash
./gradlew test --tests "gift.category.*Test" --tests "gift.member.*Test" --tests "gift.product.*Test" --tests "gift.option.*Test" --tests "gift.wish.*Test" --tests "gift.order.*Test"
```

### 인수테스트만 실행
```bash
./gradlew test --tests "gift.acceptance.*"
```

### 특정 도메인 인수테스트 실행
```bash
./gradlew test --tests "gift.acceptance.CategoryAcceptanceTest"
```

## AI 도구 활용 기록

### 사용 도구
- Claude Code (CLI)

### 활용 방식

#### 1. 예시 기반 리팩토링
MemberController에서 MemberService를 추출하는 작업을 직접 수행하여 예시를 만들었다.
이 예시를 AI에게 보여주고 나머지 Controller(Category, Product, Option, Wish, Order, KakaoAuth)에
동일한 패턴을 적용하도록 지시했다. AI가 예시의 구조(생성자 주입, 메서드 위임, 예외 처리 위치)를
그대로 따르도록 하여 일관성을 유지했다.

#### 2. 리팩토링 계획 수립
코드베이스 전체를 분석하여 리팩토링 순서와 범위를 계획하는 데 활용했다.
엔티티 의존관계를 파악하고, "구조 변경과 작동 변경을 섞지 않는다"는 원칙에 따라
커밋 단위를 설계했다.

#### 3. 테스트 코드 작성
- **단위테스트**: 도메인 엔티티와 Service 계층의 단위테스트를 작성했다.
  Mockito 기반으로 외부 의존성을 격리하고 BDD 스타일(given/when/then)을 적용했다.
- **인수테스트**: RestAssured를 사용한 HTTP 수준의 인수테스트를 작성했다.
  엔티티 의존관계 순서(Category → Product → Option → Wish → Order)로 점진적으로 구현했다.

### 코드 수정 내역

| 단계 | 작업 | 주요 변경 |
|------|------|-----------|
| 스타일 정리 | 불필요한 `@Autowired` 제거, static import 정리, `HttpStatus` ENUM 적용 | Controller 전체 |
| 서비스 추출 | Controller → Service 로직 이동 | 7개 Service 클래스 신규 생성 |
| 단위테스트 | 도메인 엔티티 + Service Mockito 테스트 | 12개 테스트 클래스 |
| 인수테스트 | RestAssured 기반 HTTP 요청/응답 검증 | 6개 테스트 클래스 (39개 테스트) |
| 비밀번호 암호화 | BCrypt 해싱 적용, Password 일급객체 도입 | Member, MemberService, AdminMemberController, V3 마이그레이션 |
| 인증 횡단관심사 분리 | `@LoginMember` + `HandlerMethodArgumentResolver` 도입 | Service에서 인증 로직 제거, Controller 파라미터 주입 방식 전환 |
| 전역 예외 처리 | `@RestControllerAdvice` + `GlobalExceptionHandler` 도입 | Controller 6개의 중복 `@ExceptionHandler` 12개를 전역 핸들러 4개로 통합 |

### 학습한 점
[LEARNING.md](LEARNING.md) 참고