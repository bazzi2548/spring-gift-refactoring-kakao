# 학습한 점

## 1. 점진적 리팩토링

구조 변경과 작동 변경을 하나의 커밋에 섞지 않는 것이 핵심이다.

- **스타일 정리** → **불필요한 코드 제거** → **서비스 추출** → **테스트 작성** 순서로 진행했다.
- 각 단계에서 기존 동작이 깨지지 않았는지 테스트로 확인한 뒤 다음 단계로 넘어갔다.
- 한 커밋에 목적 1개만 담으면 `git diff`로 의도를 빠르게 파악할 수 있고,
  문제가 생겼을 때 되돌리기도 쉽다.

## 2. 테스트 격리 전략

### 단위테스트
- Mockito의 `@Mock` + `@InjectMocks`로 외부 의존성을 격리했다.
- BDD 스타일(`given`/`when`/`then`)을 적용하여 테스트 의도를 명확히 했다.
- JPA 엔티티의 `id` 필드는 `@GeneratedValue`로 DB가 설정하므로,
  테스트에서는 리플렉션 헬퍼(`setId`)로 직접 주입했다.

### 인수테스트
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + RestAssured로 실제 HTTP 요청을 검증했다.
- `@Transactional`을 인수테스트에 쓰면 테스트 트랜잭션과 서버 트랜잭션이 달라
  실제 커밋된 데이터를 볼 수 없다. 대신 테스트 간 격리를 직접 관리했다.

## 3. 인수테스트 설계 순서

엔티티 의존관계를 따라 점진적으로 구현했다:

```
Category → Product → Option → Wish → Order
```

- Category가 없으면 Product를 만들 수 없고, Product가 없으면 Option을 만들 수 없다.
- 의존 순서대로 테스트를 작성하면 앞 단계의 API가 검증된 상태에서
  다음 단계의 테스트 픽스처로 활용할 수 있다.

## 4. AI 산출물 검증의 중요성

AI가 생성한 코드를 그대로 커밋하면 안 된다. 실제로 발생한 문제들:

- **컴파일 에러 누락**: Password 일급객체 도입 후 `AdminMemberController`,
  `MemberServiceTest` 등에서 여전히 `String`을 전달하는 코드가 남아있었다.
- **불필요한 의존성 잔류**: Service에서 인증 로직을 제거한 뒤에도
  `AuthenticationResolver` import와 생성자 파라미터가 남아있었다.
- **테스트 미수정**: 프로덕션 코드 변경 후 테스트 코드의 생성자 호출이
  맞지 않아 컴파일이 실패했다.

매 변경 후 `./gradlew test`로 전체 테스트를 돌려 확인하는 습관이 필수다.

## 5. 비밀번호 암호화 (BCrypt)

### 키워드

| 키워드 | 무엇인가 | 왜 사용하는가 |
|--------|----------|--------------|
| `BCryptPasswordEncoder` | BCrypt 해싱 알고리즘을 구현한 Spring의 `PasswordEncoder` 구현체. 내부에 랜덤 salt를 자동 생성하여 같은 평문도 매번 다른 해시를 만든다. | 비밀번호는 복호화할 수 없는 형태로 저장해야 한다. MD5, SHA-256 같은 범용 해시는 속도가 빨라 무차별 대입(Brute Force)과 Rainbow Table 공격에 취약하다. BCrypt는 **adaptive cost** 구조로 의도적으로 연산을 느리게 만들어 공격 비용을 극단적으로 높인다. salt가 해시에 내장되므로 Rainbow Table도 무력화된다. |
| `spring-security-crypto` | Spring Security에서 암호화 유틸리티(`PasswordEncoder`, `BCryptPasswordEncoder` 등)만 분리한 경량 모듈. | 프레임워크의 특정 기능만 필요할 때 전체 모듈을 가져오면 불필요한 자동 구성과 의존성이 딸려온다. `spring-security-crypto`는 암호화 관련 클래스만 포함하여 **최소 의존성 원칙**을 지킬 수 있다. `spring-boot-starter-security`와 달리 `SecurityFilterChain` 자동 구성이 활성화되지 않아 기존 인증 체계와 충돌하지 않는다. |
| `PasswordEncoder` | Spring Security가 제공하는 비밀번호 인코딩/비교 인터페이스. `encode()`와 `matches()` 두 메서드를 정의한다. | 구체 구현(BCrypt, Argon2, SCrypt 등)에 의존하지 않고 **인터페이스에 의존**하면, 해싱 알고리즘을 교체할 때 호출 코드를 변경하지 않아도 된다(OCP). 테스트에서도 `NO_OP_ENCODER` 같은 가벼운 구현체로 교체하여 테스트 속도를 확보할 수 있다. |
| `@Bean` | Spring IoC 컨테이너에 객체를 싱글턴으로 등록하는 메서드 어노테이션. `@Configuration` 클래스 내에서 사용한다. | 객체 생성과 생명주기를 컨테이너가 관리하면, 여러 곳에서 `new`로 인스턴스를 각각 생성하는 대신 **하나의 인스턴스를 공유**할 수 있다. 생성자 주입을 통해 의존성이 명시적으로 드러나고, 테스트 시 Mock으로 교체하기도 쉽다. |
| Flyway 마이그레이션 | SQL 기반의 DB 스키마 버전 관리 도구. `V1__`, `V2__`, `V3__` 순서로 마이그레이션을 실행한다. | DB 스키마와 데이터 변경을 코드로 관리하면 **버전 추적**, **재현 가능한 배포**, **팀 간 동기화**가 보장된다. 수동 SQL 실행은 누락·중복·순서 오류가 발생하지만, Flyway는 이미 적용된 마이그레이션을 건너뛰고 미적용분만 순서대로 실행한다. |

### 구현 원리

#### 의존성 선택: `spring-security-crypto` vs `spring-boot-starter-security`

비밀번호 해싱만 필요한 상황에서 두 가지 선택지가 있다:

| | `spring-security-crypto` | `spring-boot-starter-security` |
|---|---|---|
| 포함 범위 | `BCryptPasswordEncoder` 등 해싱 유틸리티만 | Security 프레임워크 전체 (필터 체인, CSRF, 폼 로그인 등) |
| 자동 구성 | 없음 | `SecurityFilterChain` 자동 활성화 |
| 부작용 | 없음 | 모든 엔드포인트에 인증 요구, CSRF 보호 활성화 |

이 프로젝트는 JWT 기반 인증을 `AuthenticationResolver`로 직접 구현했다.
`spring-boot-starter-security`를 추가하면 Spring Security의 자동 구성이
기존 인증 방식과 충돌한다 (모든 요청이 401로 차단됨).
따라서 해싱 기능만 들어있는 `spring-security-crypto`를 선택했다.

```kotlin
// build.gradle.kts
implementation("org.springframework.security:spring-security-crypto")
```

#### BCrypt 알고리즘의 동작 원리

BCrypt는 Blowfish 암호를 기반으로 한 적응형 해시 함수다:

1. **내부 salt 자동 생성**: `encode()` 호출 시마다 16바이트 랜덤 salt가 생성된다.
   같은 평문 `"password"`를 두 번 해싱하면 매번 다른 결과가 나온다.
2. **해시 형식**: `$2a$10$bSHGDjrJfM7bWUFFu9B6He2cFbz3np/3wybtRUy8vmoC5hMi6NIN6`
   - `$2a$` — BCrypt 버전
   - `10$` — cost factor (2^10 = 1024번 반복)
   - 나머지 — salt(22자) + 해시(31자)
3. **비교 방식**: salt가 해시 문자열에 포함되어 있으므로 `matches()`가 동일한 salt로 재해싱하여 비교한다.
   `equals()`로 해시끼리 비교하면 salt가 다르기 때문에 항상 실패한다.

#### Bean 등록

`BCryptPasswordEncoder`를 직접 `new`로 생성하면 여러 곳에서 각각 인스턴스를 만들게 된다.
`@Bean`으로 등록하면 Spring이 싱글턴으로 관리하고, 생성자 주입으로 일관되게 사용할 수 있다.

```java
// gift/auth/PasswordEncoderConfig.java
@Configuration
public class PasswordEncoderConfig {
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 구현 코드

#### 회원가입: 평문 저장 → 해싱 후 저장

```java
// Before
public TokenResponse register(MemberRequest request) {
    Member member = memberRepository.save(
        new Member(request.email(), request.password())  // 평문 그대로 저장
    );
    ...
}

// After
public TokenResponse register(MemberRequest request) {
    Password password = new Password(request.password(), passwordEncoder);  // 생성 시점에 해싱
    Member member = memberRepository.save(new Member(request.email(), password));
    ...
}
```

#### 로그인: 평문 비교 → matches 비교

```java
// Before — 평문 equals 비교
public TokenResponse login(MemberRequest request) {
    Member member = memberRepository.findByEmail(request.email())
        .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

    if (!member.getPassword().equals(request.password())) {
        throw new IllegalArgumentException("Invalid email or password.");
    }
    ...
}

// After — BCrypt matches 비교
public TokenResponse login(MemberRequest request) {
    Member member = memberRepository.findByEmail(request.email())
        .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

    if (!member.checkPassword(request.password(), passwordEncoder)) {
        throw new IllegalArgumentException("Invalid email or password.");
    }
    ...
}
```

#### 관리자 페이지: 회원 생성/수정에도 동일 적용

```java
// AdminMemberController.java
@PostMapping
public String create(@RequestParam String email, @RequestParam String password, Model model) {
    memberRepository.save(new Member(email, new Password(password, passwordEncoder)));
    return "redirect:/admin/members";
}

@PostMapping("/{id}/edit")
public String update(@PathVariable Long id, @RequestParam String email, @RequestParam String password) {
    Member member = memberRepository.findById(id).orElseThrow(...);
    member.update(email, new Password(password, passwordEncoder));
    memberRepository.save(member);
    return "redirect:/admin/members";
}
```

#### 기존 데이터 마이그레이션 (Flyway)

기존 V2 마이그레이션에서 삽입된 평문 비밀번호를 BCrypt 해시로 변환한다.
해시값은 `BCryptPasswordEncoder.encode()`로 사전 생성했다.

```sql
-- V3__Encrypt_member_passwords.sql
update member set password = '$2a$10$bSHGDjrJfM7bWUFFu9B6He...' where email = 'admin@example.com';   -- admin1234
update member set password = '$2a$10$.sxuUrvebDvDlLnSmsRbSO...' where email = 'user1@example.com';   -- password1
update member set password = '$2a$10$Alv2VmrLd21d0mu7h12.n....' where email = 'user2@example.com';   -- password2
```

### 사용 예시

```java
// 1. 인코딩 — 같은 평문이라도 매번 다른 해시 생성
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
encoder.encode("password");  // "$2a$10$abc..."
encoder.encode("password");  // "$2a$10$xyz..."  (다름!)

// 2. 비교 — 반드시 matches() 사용
encoder.matches("password", "$2a$10$abc...");  // true
"$2a$10$abc...".equals("$2a$10$xyz...");       // false (이렇게 비교하면 안 됨)

// 3. 프로젝트에서의 흐름
// 가입: 평문 "password" → Password 생성자 → encode → DB에 "$2a$10$..." 저장
// 로그인: 평문 "password" + DB의 "$2a$10$..." → matches → true/false
```

### 장단점

**장점:**
- 평문 저장 취약점을 제거하여 DB 유출 시에도 원본 비밀번호를 복원할 수 없다.
- `spring-security-crypto`만 사용하므로 기존 JWT 인증 방식과 충돌 없이 도입할 수 있다.
- BCrypt의 cost factor를 조절하여 해싱 강도를 높일 수 있다 (기본값 10, 최대 31).

**단점 또는 트레이드오프:**
- BCrypt 해싱은 의도적으로 느리게 설계되어 있어 로그인 요청마다 CPU 비용이 발생한다 (cost 10 기준 약 100ms).
- Flyway 마이그레이션에 해시값을 하드코딩하므로, 기본 데이터가 변경되면 새 마이그레이션 파일이 필요하다.
- `spring-security-crypto`는 `PasswordEncoder` 인터페이스를 포함하지만,
  이후 Spring Security 전체를 도입하면 Bean 충돌이 발생할 수 있어 타입을 `BCryptPasswordEncoder`로 구체화했다.

### 기대효과
- OWASP 비밀번호 저장 권장 사항(A02:2021 - Cryptographic Failures)을 충족한다.
- 기존 평문 데이터가 Flyway V3 마이그레이션으로 자동 변환되므로, 배포 시 수동 작업이 필요 없다.
- 인수테스트는 HTTP 요청 → Service에서 encode/matches 처리되므로 변경 없이 그대로 통과한다.

---

## 6. Password 일급객체 (`@Embeddable` 값 객체)

### 키워드

| 키워드 | 무엇인가 | 왜 사용하는가 |
|--------|----------|--------------|
| 일급객체(First-class Object) | 원시 타입이나 `String` 같은 범용 타입을 도메인 전용 클래스로 감싸는 패턴. `String password` 대신 `Password password`로 타입을 부여한다. | `String`은 이메일도, 비밀번호도, 주소도 될 수 있어 **타입만으로 의미를 구분할 수 없다**. 일급객체로 감싸면 도메인 규칙(생성 시 인코딩 강제, 비교는 `matches()`만 허용)을 **타입 시스템이 강제**하여, 규칙 위반을 런타임이 아닌 컴파일 타임에 잡을 수 있다. 메서드 시그니처에서 `(String, String, String)` 대신 `(Email, Password, Name)`으로 의미가 드러나 가독성도 높아진다. |
| `@Embeddable` | JPA 어노테이션. 이 클래스가 독립 엔티티가 아니라 다른 엔티티에 **포함**되는 값 타입임을 선언한다. 별도 테이블을 만들지 않고 소유 엔티티의 테이블 컬럼에 매핑된다. | 독립적인 생명주기가 없고 소유 엔티티에 종속된 값은 별도 테이블로 분리하면 **불필요한 JOIN과 FK 관리 비용**이 생긴다. `@Embeddable`은 값 객체를 자바 클래스로 분리하면서도 DB에서는 소유 엔티티 테이블의 컬럼으로 유지하여, **객체 모델의 풍부함과 DB 단순성을 동시에** 확보한다. |
| `@Embedded` | `@Embeddable` 클래스를 소유 엔티티의 필드로 포함시키는 JPA 어노테이션. | `@Embeddable`로 선언된 값 타입을 엔티티 필드에 포함할 때 사용한다. JPA에게 "이 필드는 단순 컬럼이 아니라 별도 클래스에 매핑된 복합 값"임을 알려, 해당 클래스의 필드들을 소유 엔티티 테이블의 컬럼으로 펼쳐서(flatten) 매핑한다. |
| 값 객체(Value Object) | DDD에서 정의하는 개념. 식별자(id) 없이 **속성 값 자체로 동등성을 판단**하는 객체. 불변(immutable)이어야 하고, 교체 시 새 인스턴스를 생성한다. | 엔티티는 식별자로 구분하지만(같은 이름의 회원 두 명은 다른 존재), 값 객체는 값으로 구분한다(1000원 두 개는 같은 가치). 비밀번호, 금액, 주소 같은 개념은 "어떤 값인가"가 중요하지 "몇 번째 인스턴스인가"는 의미 없다. 값 객체를 불변으로 만들면 **공유 참조에 의한 부작용(side effect)**을 원천 차단할 수 있다. |
| `NO_OP_ENCODER` | 테스트 전용 `PasswordEncoder` 구현체. `encode()`가 입력을 그대로 반환하고, `matches()`는 평문 `equals()` 비교를 한다. | 단위테스트의 관심사는 비밀번호 해싱이 아니라 비즈니스 로직이다. 실제 BCrypt를 사용하면 테스트마다 **~100ms 해싱 비용**이 발생하여 전체 테스트 스위트가 느려진다. 인터페이스 기반 설계(`PasswordEncoder`) 덕분에 테스트에서는 가벼운 구현체로 **행위만 동일하게 유지**하면서 성능을 확보할 수 있다(Test Double 패턴). |
| null safety | null 참조로 인한 `NullPointerException`을 방지하는 프로그래밍 관행. | `null`은 "값이 없음"과 "초기화되지 않음"을 구분할 수 없고, 호출 체인 어디서든 NPE를 발생시킬 수 있다. 선택적 필드(nullable)는 사용 전에 반드시 null 체크를 하고, **null이 가능한 경우를 메서드 시그니처나 분기로 명시**하여 호출자가 실수하지 않도록 해야 한다. |

### 구현 원리

#### 일급객체란?

원시 타입이나 `String` 같은 범용 타입을 그대로 사용하면 도메인 규칙이 여러 곳에 흩어진다.
비밀번호를 `String`으로 다루면:
- Service A에서는 `encoder.encode()` 후 저장하고
- Service B에서는 `encode()`를 빼먹고 평문을 저장할 수 있다.

비밀번호를 전용 클래스로 감싸면 **생성 시점에 반드시 인코딩**되도록 강제할 수 있다.
이것이 "일급객체"의 핵심 — 도메인 규칙을 타입 시스템으로 보장하는 것이다.

#### `@Embeddable`과 `@Embedded`

JPA의 `@Embeddable`은 별도 테이블을 만들지 않고, 소유 엔티티의 테이블에 컬럼으로 매핑한다.

```
-- @Entity + @Embedded 사용 시 실제 DB 스키마 (변경 없음)
CREATE TABLE member (
    id BIGINT AUTO_INCREMENT,
    email VARCHAR(255),
    password VARCHAR(255),   ← Password.password 필드가 이 컬럼에 매핑
    ...
);
```

`@Entity`로 만들면 별도 테이블 + JOIN이 필요하지만, `@Embeddable`은 기존 스키마를 그대로 유지한다.
Password는 독립적인 생명주기가 없고 Member에 종속되므로 `@Embeddable`이 적합하다.

#### null 처리 (카카오 로그인)

카카오 로그인 사용자는 비밀번호 없이 가입한다 (`new Member(email)`).
이 경우 `password` 필드가 null이므로, `checkPassword()` 호출 시 NPE를 방지해야 한다.

```java
public boolean checkPassword(String rawPassword, PasswordEncoder encoder) {
    if (password == null) {    // 카카오 전용 회원 → 비밀번호 로그인 불가
        return false;
    }
    return password.matches(rawPassword, encoder);
}
```

### 구현 코드

#### Password 값 객체

```java
@Embeddable
public class Password {
    @Column
    private String password;

    protected Password() {}  // JPA 기본 생성자 (외부 사용 불가)

    public Password(String password, PasswordEncoder encoder) {
        this.password = encoder.encode(password);  // 생성 = 즉시 인코딩
    }

    public boolean matches(String password, PasswordEncoder encoder) {
        return encoder.matches(password, this.password);  // 비교는 matches()만 허용
    }

    public String getPassword() {
        return password;  // 관리자 페이지 등 해시 조회용
    }
}
```

#### Member 엔티티 변경

```java
// Before — String password
@Entity
public class Member {
    private String password;

    public Member(String email, String password) {
        this.email = email;
        this.password = password;  // 평문이든 해시든 아무 문자열이나 들어감
    }
}

// After — Password 값 객체
@Entity
public class Member {
    @Embedded
    private Password password;

    public Member(String email, Password password) {
        this.email = email;
        this.password = password;  // Password 생성자에서 이미 인코딩 완료
    }

    public Member(String email) {  // 카카오 로그인용 (비밀번호 없음)
        this.email = email;
    }
}
```

#### Service에서의 사용

```java
// MemberService.java
public TokenResponse register(MemberRequest request) {
    Password password = new Password(request.password(), passwordEncoder);
    Member member = memberRepository.save(new Member(request.email(), password));
    ...
}

public TokenResponse login(MemberRequest request) {
    Member member = memberRepository.findByEmail(request.email()).orElseThrow(...);
    if (!member.checkPassword(request.password(), passwordEncoder)) {  // Member에 위임
        throw new IllegalArgumentException("Invalid email or password.");
    }
    ...
}
```

### 사용 예시

#### 프로덕션 코드 — BCryptPasswordEncoder 주입

```java
// 회원가입
Password password = new Password("mySecret123", passwordEncoder);
// → password 내부: "$2a$10$bSHGDjrJfM7bWUFFu9B6He..."

Member member = new Member("user@email.com", password);
memberRepository.save(member);

// 로그인
member.checkPassword("mySecret123", passwordEncoder);  // true
member.checkPassword("wrongPass", passwordEncoder);     // false

// 카카오 회원 (비밀번호 없음)
Member kakaoMember = new Member("kakao@email.com");
kakaoMember.checkPassword("anything", passwordEncoder);  // false (NPE 안 남)

// 관리자 페이지 — 회원 수정
member.update("new@email.com", new Password("newPass", passwordEncoder));
```

#### 단위테스트 — NO_OP_ENCODER로 해싱 비용 제거

```java
// BCrypt를 사용하면 테스트마다 ~100ms 해싱 비용이 발생한다.
// NO_OP_ENCODER는 입력을 그대로 반환하여 테스트 속도를 유지한다.
private static final PasswordEncoder NO_OP_ENCODER = new PasswordEncoder() {
    @Override
    public String encode(CharSequence rawPassword) {
        return rawPassword.toString();  // 해싱 없이 그대로 반환
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword.toString().equals(encodedPassword);  // 평문 비교
    }
};

// 테스트 헬퍼
private Member createMember(String email, String password) {
    return new Member(email, new Password(password, NO_OP_ENCODER));
}

// 테스트 코드
@Test
void createWithEmailAndPassword() {
    Member member = createMember("test@email.com", "password");

    assertThat(member.getEmail()).isEqualTo("test@email.com");
    assertThat(member.getPassword()).isEqualTo("password");  // NO_OP이므로 평문 그대로
}

@Test
void checkPasswordWithNullPassword() {
    Member member = new Member("kakao@email.com");  // password = null

    assertThat(member.checkPassword("any", NO_OP_ENCODER)).isFalse();  // NPE 없이 false
}
```

#### Service 단위테스트 — Mock BCryptPasswordEncoder

```java
@Mock
private BCryptPasswordEncoder passwordEncoder;

@Test
void registerSuccess() {
    given(passwordEncoder.encode("password")).willReturn("hashed");
    // → new Password("password", passwordEncoder) 호출 시 내부에 "hashed" 저장
    ...
}

@Test
void loginSuccess() {
    Member member = createMember("test@email.com", "hashed");
    given(passwordEncoder.matches("password", "hashed")).willReturn(true);
    // → member.checkPassword("password", passwordEncoder) → true
    ...
}
```

### 장단점

**장점:**
- **평문 저장 원천 차단**: `Password` 생성자가 반드시 `encode()`를 호출하므로, 어떤 Service에서 생성하든 인코딩이 보장된다.
- **도메인 로직 캡슐화**: 비밀번호 비교 로직이 `Password.matches()` 안에 있어 Service가 인코딩 방식을 몰라도 된다.
- **스키마 변경 없음**: `@Embeddable`이므로 기존 `member` 테이블의 `password` 컬럼을 그대로 사용한다.
- **null safety**: 카카오 전용 회원(`password == null`)도 `checkPassword()`에서 안전하게 처리된다.
- **테스트 용이성**: `NO_OP_ENCODER` 패턴으로 해싱 비용 없이 빠르게 검증할 수 있다.

**단점 또는 트레이드오프:**
- **인코더 의존성 전파**: `Password` 생성 시 `PasswordEncoder`를 전달해야 하므로, 엔티티를 생성하는 모든 곳(Service, Controller)에서 인코더를 알아야 한다.
- **JPA 기본 생성자 강제**: `@Embeddable`은 `protected` 기본 생성자가 필요하다. 이를 통해 인코딩 없이 객체를 만들 수 있는 허점이 존재하지만, `protected`로 제한하여 외부 사용을 막았다.
- **컬럼명 매핑**: `Password` 내부 필드명이 `password`이므로 `member.password` 컬럼에 자동 매핑된다. 필드명이 다르면 `@Column(name = "...")` 지정이 필요하다.

### 기대효과
- 비밀번호 관련 로직이 `Password` 클래스 한 곳에 집중되어 정책 변경(해싱 알고리즘 교체, 비밀번호 복잡도 검증 추가) 시 영향 범위가 최소화된다.
- `String` 대신 `Password` 타입을 사용하므로 컴파일 타임에 실수를 잡을 수 있다 (예: 평문 문자열을 Member 생성자에 직접 전달 불가).
- 동일한 값 객체 패턴을 다른 도메인(예: `Email`, `PhoneNumber`)에도 적용할 수 있는 선례를 만들었다.

---

## 7. 횡단관심사 분리 (`HandlerMethodArgumentResolver`)

### 키워드

| 키워드 | 무엇인가 | 왜 사용하는가 |
|--------|----------|--------------|
| 횡단관심사(Cross-cutting Concern) | 여러 모듈에 걸쳐 반복되는 관심사. 인증, 로깅, 트랜잭션 등이 대표적이다. 비즈니스 로직과 직교(orthogonal)하여 별도로 분리할 수 있다. | 횡단관심사가 비즈니스 로직에 섞이면 **동일한 코드가 N개 모듈에 복사**된다. 인증 방식을 변경할 때 N곳을 모두 수정해야 하며, 한 곳이라도 빠뜨리면 보안 구멍이 생긴다. 횡단관심사를 한 곳으로 분리하면 **변경 지점이 1개**가 되어 유지보수성과 안전성이 동시에 향상된다. |
| `HandlerMethodArgumentResolver` | Spring MVC 인터페이스. Controller 메서드의 파라미터를 HTTP 요청 정보로부터 해석(resolve)하여 주입하는 역할. `@RequestParam`, `@PathVariable` 등도 내부적으로 이 체인을 통해 처리된다. | Controller 메서드가 호출되기 **전에** 파라미터를 가공·검증·변환할 수 있는 확장 포인트다. 인증, 페이징, 국제화(Locale) 등 **모든 Controller에 공통으로 필요한 파라미터 해석 로직**을 한 곳에 집중시켜, Controller가 비즈니스 로직에만 집중하도록 한다. Spring MVC의 파라미터 해석 체인에 자연스럽게 끼어들기 때문에 기존 프레임워크 흐름을 깨뜨리지 않는다. |
| 커스텀 마커 어노테이션 (`@LoginMember`) | 로직 없이 **의미만 전달**하는 어노테이션. 특정 프레임워크 컴포넌트(ArgumentResolver, AOP 등)가 이 어노테이션을 감지하여 처리한다. | 타입만으로는 의도를 구분할 수 없을 때(같은 `Member` 타입이라도 인증된 회원인지, 대상 회원인지 다름) **어노테이션으로 의미를 명시**한다. ArgumentResolver가 모든 `Member` 타입을 무조건 처리하면 의도치 않은 주입이 발생하므로, 어노테이션으로 **명시적 opt-in** 방식을 사용하여 처리 대상을 정확히 지정한다. |
| `@Target(ElementType.PARAMETER)` | 어노테이션이 적용될 수 있는 위치를 제한하는 메타 어노테이션. `PARAMETER`는 메서드 파라미터에만 허용한다. | 어노테이션의 사용 위치를 제한하지 않으면 클래스, 메서드, 필드 등 **의도하지 않은 곳에 사용되는 실수를 컴파일러가 잡아줄 수 없다**. `@Target`으로 허용 위치를 명시하면 잘못된 사용을 컴파일 타임에 차단하여 안전성을 높인다. |
| `@Retention(RetentionPolicy.RUNTIME)` | 어노테이션 정보가 유지되는 시점을 지정하는 메타 어노테이션. `SOURCE`(컴파일 시 제거), `CLASS`(클래스 파일까지 유지), `RUNTIME`(실행 중 리플렉션 접근 가능) 세 단계가 있다. | 프레임워크가 **런타임에 리플렉션으로 어노테이션을 읽어야 하는 경우** 반드시 `RUNTIME`이어야 한다. `SOURCE`는 Lombok처럼 컴파일 시점에만 필요한 경우, `CLASS`는 바이트코드 분석 도구용이다. Spring, JPA 등 런타임 프레임워크와 연동하는 커스텀 어노테이션은 대부분 `RUNTIME`을 사용한다. |
| `WebMvcConfigurer` | Spring MVC의 설정을 커스터마이징할 수 있는 인터페이스. 인터셉터, 뷰 리졸버, ArgumentResolver, CORS 설정 등을 등록할 수 있다. | Spring MVC는 기본 제공 `ArgumentResolver` 외에 커스텀 구현체를 자동 감지하지 않는다. `WebMvcConfigurer`를 구현하면 Spring Boot의 자동 구성을 **유지하면서도** 필요한 부분만 확장할 수 있다. `@EnableWebMvc`와 달리 기존 자동 구성을 덮어쓰지 않으므로 안전하다. |

### 구현 원리

#### 문제: 인증 로직의 산재

변경 전 구조에서 인증 관련 코드가 3개 계층에 걸쳐 반복되고 있었다:

```
Controller (Authorization 헤더 수신)
    → Service (resolveMember 호출 → AuthenticationResolver 의존)
        → AuthenticationResolver (JWT 파싱 → Member 조회)
```

모든 인증이 필요한 Controller 메서드마다 `@RequestHeader("Authorization") String authorization`을 받고,
Service에 `resolveMember()` 메서드가 있어야 했다.
Service가 비즈니스 로직과 인증 로직을 동시에 담당하는 것은 단일 책임 원칙 위반이다.

#### 해결: Spring MVC의 ArgumentResolver 체인

Spring MVC는 Controller 메서드를 호출하기 전에 파라미터를 해석하는 과정을 거친다:

```
HTTP 요청 → DispatcherServlet → HandlerAdapter
    → ArgumentResolver 체인 (파라미터별로 적합한 resolver 탐색)
        → supportsParameter() == true인 resolver 발견
        → resolveArgument() 호출 → 반환값을 Controller 파라미터에 주입
    → Controller 메서드 실행
```

커스텀 `ArgumentResolver`를 등록하면 이 체인에 끼어들 수 있다.
`@LoginMember`가 붙은 `Member` 타입 파라미터를 만나면,
Authorization 헤더에서 JWT를 추출하고 Member를 조회하여 자동 주입한다.

#### 커스텀 어노테이션의 역할

`@LoginMember`는 마커 어노테이션이다. 로직은 없고 "이 파라미터는 인증된 회원이다"라는 의미를 전달한다.

```java
@Target(ElementType.PARAMETER)    // 메서드 파라미터에만 사용 가능
@Retention(RetentionPolicy.RUNTIME)  // 런타임에 리플렉션으로 읽을 수 있어야 함
public @interface LoginMember {}
```

- `@Target(PARAMETER)` — 클래스나 메서드에 실수로 붙이는 것을 컴파일 타임에 방지한다.
- `@Retention(RUNTIME)` — Spring MVC가 런타임에 어노테이션 존재 여부를 확인해야 하므로 반드시 RUNTIME이어야 한다.

#### WebMvcConfigurer 등록

커스텀 `ArgumentResolver`는 자동 등록되지 않는다. `WebMvcConfigurer`를 통해 명시적으로 등록해야 한다.
이 설정은 특정 도메인이 아닌 애플리케이션 전체에 영향을 주므로 `gift.config` 패키지에 배치했다.

```java
// gift/config/WebMvcConfig.java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final LoginMemberArgumentResolver loginMemberArgumentResolver;

    public WebMvcConfig(LoginMemberArgumentResolver loginMemberArgumentResolver) {
        this.loginMemberArgumentResolver = loginMemberArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }
}
```

### 구현 코드

#### LoginMemberArgumentResolver

```java
@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {
    private final AuthenticationResolver authenticationResolver;

    public LoginMemberArgumentResolver(AuthenticationResolver authenticationResolver) {
        this.authenticationResolver = authenticationResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // @LoginMember가 붙어있고, 타입이 Member인 파라미터만 처리
        return parameter.hasParameterAnnotation(LoginMember.class)
            && parameter.getParameterType().equals(Member.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String authorization = request.getHeader("Authorization");
        Member member = authenticationResolver.extractMember(authorization);
        if (member == null) {
            throw new IllegalStateException("Unauthorized");
        }
        return member;
    }
}
```

#### Controller 변경 (Before → After)

```java
// Before — WishController (인증 로직 직접 처리)
@GetMapping
public ResponseEntity<Page<WishResponse>> getWishes(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable) {
    Member member = wishService.resolveMember(authorization);  // Service에 인증 위임
    return ResponseEntity.ok(wishService.findByMember(member, pageable));
}

@PostMapping
public ResponseEntity<WishResponse> addWish(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody WishRequest request) {
    Member member = wishService.resolveMember(authorization);  // 매 메서드마다 반복
    ...
}

// After — WishController (@LoginMember로 자동 주입)
@GetMapping
public ResponseEntity<Page<WishResponse>> getWishes(
        @LoginMember Member member,    // ArgumentResolver가 자동 주입
        Pageable pageable) {
    return ResponseEntity.ok(wishService.findByMember(member, pageable));
}

@PostMapping
public ResponseEntity<WishResponse> addWish(
        @LoginMember Member member,    // 선언만 하면 인증 완료
        @Valid @RequestBody WishRequest request) {
    ...
}
```

#### Service 변경 (Before → After)

```java
// Before — OrderService (인증 로직 포함)
@Service
public class OrderService {
    private final AuthenticationResolver authenticationResolver;  // 인증 의존성
    ...

    public Member resolveMember(String authorization) {  // 인증 메서드
        Member member = authenticationResolver.extractMember(authorization);
        if (member == null) { throw new IllegalStateException("Unauthorized"); }
        return member;
    }

    public OrderResponse create(Member member, OrderRequest request) { ... }
}

// After — OrderService (비즈니스 로직만)
@Service
public class OrderService {
    // AuthenticationResolver 의존성 제거됨
    ...

    // resolveMember() 메서드 제거됨

    public OrderResponse create(Member member, OrderRequest request) { ... }
}
```

### 사용 예시

#### 새로운 Controller에 인증 적용하기

```java
// 새로운 ReviewController를 추가할 때
// @LoginMember만 선언하면 인증이 자동 적용된다.
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @LoginMember Member member,     // 이것만으로 인증 완료
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.create(member, request));
    }

    // 인증이 필요 없는 엔드포인트는 @LoginMember를 안 붙이면 됨
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.findById(id));
    }
}
```

#### 인증 실패 시 흐름

```
1. 클라이언트가 Authorization 헤더 없이 요청
2. LoginMemberArgumentResolver.resolveArgument() 실행
3. authenticationResolver.extractMember(null) → null 반환
4. throw new IllegalStateException("Unauthorized")
5. Controller의 @ExceptionHandler(IllegalStateException.class) 처리
6. → 401 Unauthorized 응답
```

#### 인증 방식 변경 시 영향 범위

```
JWT → OAuth2로 인증 방식을 변경한다고 가정:

Before (resolveMember 패턴):
  - WishService.resolveMember() 수정
  - OrderService.resolveMember() 수정
  - 기타 모든 Service의 resolveMember() 수정

After (@LoginMember 패턴):
  - LoginMemberArgumentResolver.resolveArgument() 하나만 수정
  - Controller, Service 변경 없음
```

### 장단점

**장점:**
- **Controller 단순화**: 인증 관련 파라미터(`@RequestHeader`)와 로직(`resolveMember`)이 사라져 비즈니스 흐름만 남는다.
- **Service 순수화**: 인증 의존성(`AuthenticationResolver`)이 제거되어 단위테스트에서 인증 관련 Mock이 불필요해진다.
- **단일 변경 지점**: 인증 방식이 변경되어도 `LoginMemberArgumentResolver` 하나만 수정하면 전체 Controller에 적용된다.
- **선언적 인증**: `@LoginMember`를 붙이면 인증 적용, 안 붙이면 비인증 — 의도가 명확하다.

**단점 또는 트레이드오프:**
- **설정 클래스 추가**: `WebMvcConfigurer`를 구현한 설정 클래스가 필요하다. 등록을 빠뜨리면 `@LoginMember`가 무시되어 null이 주입될 수 있다.
- **암묵적 동작**: Controller 코드만 보면 `Member`가 어디서 오는지 바로 보이지 않는다. `@LoginMember`라는 어노테이션의 존재를 알아야 한다.
- **예외 흐름 분리**: 인증 실패 시 `ArgumentResolver`에서 예외가 발생하므로, Controller의 `@ExceptionHandler`로 처리하려면 해당 예외 타입을 핸들러에 등록해야 한다.
- **테스트**: 인수테스트는 HTTP 요청이므로 자연스럽게 동작하지만, Controller 단위테스트(MockMvc)에서는 `ArgumentResolver`를 직접 등록하거나 Mock해야 한다.

### 기대효과
- 인증 관련 보일러플레이트가 Controller 3개(Wish, Order, KakaoAuth) × 메서드 2~3개 = 약 7곳에서 제거된다.
- Service 계층에서 `AuthenticationResolver` 의존성과 `resolveMember()` 메서드가 완전히 제거되어, 비즈니스 로직 테스트가 간결해진다.
- 새로운 인증 필요 엔드포인트 추가 시 `@LoginMember Member member` 한 줄이면 충분하다.
