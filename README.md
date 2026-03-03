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

### 학습한 점
- **점진적 리팩토링**: 구조 변경(리팩토링)과 작동 변경(기능 추가)을 분리하면 각 커밋의
  의도가 명확해지고, 문제 발생 시 원인을 빠르게 찾을 수 있다.
- **테스트 격리 전략**: Flyway 초기 데이터와 테스트 데이터가 공존할 때,
  `greaterThanOrEqualTo()`, `hasItems()` 같은 유연한 매처를 사용하면
  테스트 간 간섭 없이 검증할 수 있다.
- **인수테스트 설계 순서**: 엔티티 의존관계가 작은 것부터 테스트를 작성하면
  헬퍼 메서드를 자연스럽게 재사용할 수 있고, 실패 원인을 좁힐 수 있다.
- **AI 산출물 검증의 중요성**: AI가 생성한 `@MockBean`이 실제로는 불필요한 경우가 있었다.
  V2 초기 데이터의 멤버에 `kakaoAccessToken`이 null이므로 외부 API 호출이 발생하지 않아
  린터가 제거한 것이 올바른 판단이었다. AI 코드를 그대로 수용하지 않고 검토하는 과정이 필요하다.

#### 비밀번호 암호화 — `spring-security-crypto` vs `spring-boot-starter-security`

비밀번호 해싱만 필요한 경우 `spring-boot-starter-security`를 추가하면
SecurityFilterChain 자동 구성이 활성화되어 CSRF 보호, 폼 로그인, 모든 엔드포인트 차단이
일어난다. 이를 다시 비활성화하려면 별도의 SecurityConfig를 작성해야 하므로 과하다.
`spring-security-crypto` 모듈만 추가하면 `BCryptPasswordEncoder`만 사용할 수 있어
기존 코드에 영향 없이 비밀번호 해싱을 적용할 수 있다.

기존에 평문으로 저장된 비밀번호는 Flyway 마이그레이션(V3)으로 BCrypt 해시값으로 일괄
UPDATE하여 변환했다. 해시값은 프로젝트 내 테스트 코드로 `BCryptPasswordEncoder.encode()`를
호출하여 생성했다.

#### Password 일급객체 — `@Embeddable` 값 객체로 도메인 로직 캡슐화

비밀번호를 `String`으로 다루면 encode/matches 로직이 Service, Controller 등에 흩어진다.
`@Embeddable Password` 클래스를 만들어 생성 시 인코딩(`encode`), 검증(`matches`)을
한 곳에 모았다. 이로 인해 발생한 변경 범위:

- `Member`의 password 필드가 `String` → `Password`로 변경
- `Member.getPassword()`는 `password.getPassword()`로 위임하되, null 안전성 처리 추가
- `Member.checkPassword(rawPassword, encoder)` 메소드를 추가하여 비밀번호 검증 책임을
  Service가 아닌 도메인 객체가 담당하도록 함
- 테스트에서는 `PasswordEncoder`의 NoOp 구현을 만들어 인코딩 없이 테스트 픽스처를 생성
  → 테스트가 BCrypt 해싱 시간에 의존하지 않아 빠르게 실행됨

일급객체 도입 시 **기존 생성자 시그니처가 변경**되므로, 해당 생성자를 사용하는 모든
테스트 파일(MemberTest, MemberServiceTest, OrderServiceTest, WishServiceTest)에
파급 효과가 발생한다. 비밀번호와 무관한 테스트(Order, Wish)는 이메일 전용 생성자
`new Member(email)`를 사용하도록 변경하여 불필요한 의존을 제거했다.

#### 횡단관심사 분리 — `HandlerMethodArgumentResolver`

인증 로직(`Authorization` 헤더 파싱 → JWT 검증 → Member 조회)이 OrderService와
WishService에 동일한 `resolveMember()` 메소드로 중복되어 있었다.
인증은 특정 도메인의 비즈니스 로직이 아니라 **횡단관심사(cross-cutting concern)**이므로
Spring MVC의 `HandlerMethodArgumentResolver`로 분리했다.

적용 과정:
1. `@LoginMember` 커스텀 어노테이션 생성 (`@Target(PARAMETER)`, `@Retention(RUNTIME)`)
2. `LoginMemberArgumentResolver` 구현 — `supportsParameter()`에서 `@LoginMember` +
   `Member.class` 조합을 확인하고, `resolveArgument()`에서 Authorization 헤더를 파싱하여
   Member 객체를 반환. 인증 실패 시 `IllegalStateException("Unauthorized")` 발생
3. `WebMvcConfig`에서 `WebMvcConfigurer.addArgumentResolvers()`로 등록
4. Controller에서 `@RequestHeader("Authorization")` + `service.resolveMember()` 호출을
   `@LoginMember Member member` 파라미터로 대체
5. Service에서 `AuthenticationResolver` 의존성과 `resolveMember()` 메소드 제거

결과: Controller는 `@LoginMember Member member`로 인증된 사용자를 바로 받고,
Service는 순수 비즈니스 로직만 담당하게 되었다. 새로운 Controller에 인증이 필요하면
파라미터에 `@LoginMember`만 붙이면 된다.

`WebMvcConfig`는 `gift.config` 패키지에 두었다. `gift.auth`에 두면 동작은 하지만,
MVC 전체 설정이므로 인증 패키지보다 공통 설정 패키지가 적절하다.