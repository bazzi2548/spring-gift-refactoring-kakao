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
5. GlobalExceptionHandler의 @ExceptionHandler(IllegalStateException.class) 처리
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

---

## 8. 전역 예외 처리 (`@RestControllerAdvice`)

### 키워드

| 키워드 | 무엇인가 | 왜 사용하는가 |
|--------|----------|--------------|
| `@RestControllerAdvice` | `@ControllerAdvice` + `@ResponseBody`의 조합. 모든 `@RestController`에 적용되는 전역 예외 핸들러, 모델 어트리뷰트, 바인딩 설정 등을 정의할 수 있는 특수 컴포넌트다. | 예외 처리는 대표적인 **횡단관심사**다. 각 Controller마다 동일한 `@ExceptionHandler`를 복사하면 예외-응답 매핑 규칙이 변경될 때 N곳을 수정해야 하고, 누락 시 일관성이 깨진다. `@RestControllerAdvice`로 한 곳에서 관리하면 **예외 처리 정책의 단일 진실 공급원(Single Source of Truth)**이 된다. |
| `@ExceptionHandler` | 특정 예외 타입이 발생했을 때 호출될 메서드를 지정하는 어노테이션. Controller 내부에 두면 해당 Controller에만, `@ControllerAdvice` 내부에 두면 전역으로 적용된다. | 예외를 try-catch로 잡아 수동으로 `ResponseEntity`를 만드는 대신, **선언적으로 예외-응답 매핑**을 정의할 수 있다. Spring MVC가 예외 발생 시 자동으로 매칭되는 핸들러를 찾아 호출하므로, 비즈니스 코드에서 예외를 던지기만 하면 된다. |
| `@ControllerAdvice` | `@RestControllerAdvice`의 상위 개념. `@ResponseBody`가 없으므로 뷰(HTML)를 반환하는 Controller에 적합하다. REST API에는 `@RestControllerAdvice`를 사용한다. | MVC 패턴에서 예외 처리, 데이터 바인딩, 모델 어트리뷰트 등 **Controller 횡단 관심사를 AOP 없이 깔끔하게 분리**할 수 있다. `basePackages`나 `assignableTypes`로 적용 범위를 제한할 수도 있다. |

### 구현 원리

#### 문제: 동일한 `@ExceptionHandler`의 반복

변경 전, 6개 Controller에 예외 핸들러가 산재해 있었다:

| 예외 | 응답 | 반복 횟수 | Controller |
|------|------|----------|------------|
| `NoSuchElementException` | 404 Not Found | 5곳 | Category, Product, Option, Order, Wish |
| `IllegalArgumentException` | 400 Bad Request | 4곳 | Product, Option, Order, Member |
| `IllegalStateException` | 401 Unauthorized | 2곳 | Order, Wish |
| `SecurityException` | 403 Forbidden | 1곳 | Wish |

총 12개의 `@ExceptionHandler` 메서드가 있었고, 핸들러 본문은 모두 동일했다.

#### Spring MVC의 예외 처리 우선순위

Spring MVC는 예외 발생 시 다음 순서로 핸들러를 탐색한다:

1. **Controller 내부의 `@ExceptionHandler`** — 해당 Controller에서 발생한 예외만 처리
2. **`@ControllerAdvice`의 `@ExceptionHandler`** — 매칭되는 Controller 범위 내 전역 처리
3. **Spring 기본 예외 처리** — `DefaultHandlerExceptionResolver` 등

Controller 내부 핸들러가 `@ControllerAdvice`보다 우선하므로, 특정 Controller에서만 다르게 처리해야 할 예외가 있다면 해당 Controller에 `@ExceptionHandler`를 남겨 오버라이드할 수 있다.

### 구현 코드

#### GlobalExceptionHandler (신규)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> handleUnauthorized(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Void> handleForbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
```

#### Controller 변경 (Before → After)

```java
// Before — OrderController (핸들러 3개 보유)
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    ...

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> handleUnauthorized(IllegalStateException e) {
        return ResponseEntity.status(UNAUTHORIZED).build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

// After — OrderController (핸들러 전부 제거, 비즈니스 로직만)
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    ...
    // @ExceptionHandler 없음 → GlobalExceptionHandler가 처리
}
```

### 사용 예시

#### 새로운 Controller 추가 시

```java
// 예외 핸들러를 작성할 필요 없이 예외를 던지기만 하면 된다.
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable Long id) {
        // NoSuchElementException → GlobalExceptionHandler가 404로 변환
        return ResponseEntity.ok(reviewService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(@RequestBody ReviewRequest request) {
        // IllegalArgumentException → GlobalExceptionHandler가 400으로 변환
        return ResponseEntity.ok(reviewService.create(request));
    }
}
```

#### 특정 Controller에서 다르게 처리해야 할 때

```java
// GlobalExceptionHandler의 기본 동작을 오버라이드할 수 있다.
// Controller 내부 @ExceptionHandler가 @ControllerAdvice보다 우선한다.
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    // 이 Controller에서는 IllegalArgumentException을 400이 아닌 422로 반환
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity().body(e.getMessage());
    }
}
```

#### 예외 처리 흐름

```
1. Service에서 throw new NoSuchElementException("Product not found")
2. Spring MVC가 해당 Controller에서 @ExceptionHandler(NoSuchElementException.class) 탐색
3. Controller에 없으면 → @RestControllerAdvice에서 탐색
4. GlobalExceptionHandler.handleNotFound() 호출
5. → 404 Not Found 응답
```

### 장단점

**장점:**
- **중복 제거**: 12개의 `@ExceptionHandler` 메서드가 4개로 통합되어 코드량이 줄어든다.
- **일관성 보장**: 예외-응답 매핑이 한 곳에서 관리되므로, 새로운 Controller를 추가할 때 핸들러를 빼먹을 수 없다.
- **변경 용이**: 에러 응답 형식을 변경할 때 (예: `body(e.getMessage())` → 구조화된 에러 DTO) `GlobalExceptionHandler`만 수정하면 전체 API에 적용된다.
- **오버라이드 가능**: Controller 내부에 `@ExceptionHandler`를 두면 전역 핸들러보다 우선 적용되므로, 특수한 경우에 유연하게 대응할 수 있다.

**단점 또는 트레이드오프:**
- **암묵적 처리**: Controller 코드만 보면 예외가 어떤 HTTP 응답으로 변환되는지 바로 보이지 않는다. `GlobalExceptionHandler`의 존재를 알아야 한다.
- **범위 제어 필요**: `@RestControllerAdvice`는 기본적으로 모든 Controller에 적용된다. Admin Controller와 API Controller의 에러 형식이 다르다면 `basePackages`나 `assignableTypes`로 범위를 분리해야 한다.
- **예외 타입 충돌**: `IllegalStateException`을 401로 매핑했지만, 인증과 무관한 `IllegalStateException`이 다른 곳에서 발생하면 의도치 않게 401이 반환될 수 있다. 장기적으로는 커스텀 예외 클래스를 정의하는 것이 안전하다.

### 기대효과
- Controller가 예외 처리 없이 비즈니스 위임만 담당하게 되어 **Controller의 역할이 더 명확**해진다.
- 에러 응답 형식을 통일하거나 변경할 때 **한 파일만 수정**하면 전체 API에 반영된다.
- 새로운 예외 타입(예: `AccessDeniedException`)을 추가할 때 `GlobalExceptionHandler`에 핸들러 하나만 추가하면 모든 Controller에서 즉시 처리된다.

---

## 9. 테스트 Fixture 패턴 (Reflection 제거)

### 키워드

| 키워드 | 무엇인가 | 왜 사용하는가 |
|--------|----------|--------------|
| Reflection | Java의 `java.lang.reflect` 패키지를 통해 런타임에 클래스의 필드, 메서드, 생성자 등에 **접근 제한(private/protected)을 무시하고** 접근하는 기술. `field.setAccessible(true)`로 private 필드를 읽거나 쓸 수 있다. | 프레임워크(Spring, JPA, Jackson 등)가 **사용자가 작성한 클래스의 내부 구조를 런타임에 분석·조작**해야 할 때 사용한다. 예: JPA가 `@Entity`의 private 필드에 DB 값을 주입하거나, Jackson이 private 필드를 JSON으로 직렬화하는 경우. 프레임워크가 아닌 **애플리케이션 코드에서 직접 사용하면 캡슐화를 깨뜨리고, 리팩토링에 취약하며, 컴파일 타임 안전성을 잃는다.** |
| 캡슐화(Encapsulation) | 객체의 내부 상태(필드)를 외부에서 직접 접근하지 못하도록 숨기고, **공개된 메서드를 통해서만 상호작용**하도록 하는 OOP 원칙. `private` 필드 + `public` 메서드가 기본 형태다. | 내부 구현을 변경해도 외부 코드에 영향을 주지 않기 위해서다. 필드명을 바꾸거나, 내부 자료구조를 변경하거나, 유효성 검증을 추가해도 공개 인터페이스가 동일하면 호출자는 수정할 필요가 없다. Reflection으로 private 필드에 직접 접근하면 이 계약이 깨져서, **내부 변경이 곧 외부 코드의 깨짐**으로 이어진다. |
| 테스트 Fixture | 테스트에서 반복적으로 필요한 **객체를 일관되게 생성**해주는 헬퍼 클래스 또는 메서드. Factory Method 패턴이나 Builder 패턴으로 구현한다. | 테스트마다 객체 생성 코드가 중복되면, 생성자 시그니처가 바뀔 때 **모든 테스트를 수정**해야 한다. Fixture에 생성 로직을 집중하면 변경 지점이 1곳이 되고, 테스트 코드는 "무엇을 검증하는가"에 집중할 수 있다. |
| 테스트 빌더 패턴 | Fixture의 한 형태로, **메서드 체이닝으로 필요한 값만 지정**하고 나머지는 기본값을 사용하는 패턴. `new OrderTestBuilder().id(100L).name("catsbi").build()` 형태로 사용한다. | 엔티티의 필드가 많을 때 Factory Method는 파라미터가 길어져 가독성이 떨어진다. Builder 패턴은 **테스트에서 관심 있는 값만 명시**하고, 나머지는 기본값으로 채워 테스트 의도를 명확히 드러낸다. |
| `protected` 접근 제한자 | 같은 패키지 내의 클래스와 하위 클래스에서만 접근할 수 있는 접근 수준. `private`보다 넓고 `public`보다 좁다. | 테스트용 생성자를 `public`으로 열면 프로덕션 코드에서도 사용 가능해져 **잘못된 사용을 유도**한다. `protected`로 제한하면 같은 패키지의 Fixture 클래스(테스트 코드)에서만 접근 가능하고, 다른 패키지의 프로덕션 코드에서는 사용할 수 없다. Java의 패키지 구조를 활용한 **자연스러운 접근 제어**다. |

### 구현 원리

#### 문제: 테스트에서 Reflection으로 id를 설정하는 패턴

JPA 엔티티의 `id` 필드는 DB가 자동 생성(`@GeneratedValue`)하므로 setter가 없다.
단위테스트(Mock 기반)에서는 DB 없이 id가 설정된 엔티티가 필요한데,
Reflection으로 private 필드에 직접 접근하는 패턴이 흔히 사용되었다:

```java
// 문제가 되는 패턴
private void setId(Object entity, Long id) throws Exception {
    Field field = entity.getClass().getDeclaredField("id");
    field.setAccessible(true);  // private 접근 제한을 강제로 해제
    field.set(entity, id);      // 필드명 "id"에 의존 (문자열 기반)
}
```

이 패턴의 위험성:
1. **캡슐화 파괴**: `setAccessible(true)`는 Java의 접근 제한을 무시한다. private으로 숨긴 이유(외부에서 임의 변경 방지)가 무효화된다.
2. **문자열 기반 참조**: `"id"`라는 필드명을 문자열로 참조하므로, 필드명이 바뀌면 **컴파일 에러 없이 런타임에 실패**한다.
3. **고비용**: Reflection은 JVM의 보안 검사를 우회하므로 일반 메서드 호출보다 수십 배 느리다. 대량 테스트에서 성능에 영향을 줄 수 있다.
4. **`throws Exception` 전파**: Reflection API는 checked exception을 던지므로, 모든 테스트 메서드에 `throws Exception`이 전파되어 실제 테스트 예외와 구분이 어려워진다.

#### 해결 방법 1: `protected` 생성자 + Fixture 팩토리 메서드 (본 프로젝트 적용)

엔티티에 `protected` 생성자를 추가하고, 같은 패키지의 Fixture 클래스에서 이를 호출한다.

```
src/main/java/gift/member/Member.java          ← protected Member(Long id, ...)
src/test/java/gift/member/MemberFixture.java    ← 같은 패키지 → protected 접근 가능
```

Java의 `protected`는 **같은 패키지 내에서 접근 가능**하므로, `src/main`과 `src/test`의 패키지가 동일하면 테스트 코드에서 `protected` 생성자를 호출할 수 있다.

#### 해결 방법 2: 테스트 빌더 패턴

필드가 많거나, 테스트마다 다른 조합의 값이 필요할 때 Builder 패턴이 유용하다:

```java
public class OrderTestBuilder {
    private Long id = 1L;
    private String name = "defaultName";

    public OrderTestBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public OrderTestBuilder name(String name) {
        this.name = name;
        return this;
    }

    public Order build() {
        Order order = new Order(name);
        ReflectionTestUtils.setField(order, "id", id);  // Spring 테스트 유틸리티 사용
        return order;
    }
}

// 사용
Order order = new OrderTestBuilder()
    .id(100L)
    .name("catsbi")
    .build();
```

빌더 내부에서 `ReflectionTestUtils.setField()`를 사용하더라도, Reflection이 **빌더 한 곳에 캡슐화**되어 있으므로 필드명 변경 시 빌더만 수정하면 된다.

#### 두 접근의 비교

| 기준 | Fixture 팩토리 (본 프로젝트) | 테스트 빌더 패턴 |
|------|--------------------------|----------------|
| Reflection 사용 | 완전 제거 | 빌더 내부에 캡슐화 |
| 엔티티 수정 | `protected` 생성자 추가 필요 | 엔티티 수정 불필요 |
| 가독성 | 파라미터가 많으면 의미 파악이 어려움 | 메서드 체이닝으로 의도 명확 |
| 적합한 경우 | 필드가 적은 엔티티 (3~5개) | 필드가 많은 엔티티, 다양한 조합 필요 시 |

### 구현 코드

#### 엔티티 — `protected` 생성자 추가

```java
// Member.java
@Entity
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;

    protected Member() {}  // JPA 기본 생성자

    public Member(String email) {       // 프로덕션용
        this.email = email;
    }

    protected Member(Long id, String email) {  // 테스트 Fixture용
        this.id = id;
        this.email = email;
    }
}

// Product.java
@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected Product(Long id, String name, int price, String imageUrl, Category category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.category = category;
    }
}

// Option.java — 동일 패턴
// Wish.java  — 동일 패턴
```

#### Fixture 클래스 (테스트 코드)

```java
// src/test/java/gift/member/MemberFixture.java
package gift.member;  // Member와 같은 패키지 → protected 접근 가능

public class MemberFixture {
    public static Member member(Long id, String email) {
        return new Member(id, email);  // protected 생성자 호출
    }
}

// src/test/java/gift/product/ProductFixture.java
package gift.product;

public class ProductFixture {
    public static Product product(Long id, String name, int price, String imageUrl, Category category) {
        return new Product(id, name, price, imageUrl, category);
    }
}

// src/test/java/gift/option/OptionFixture.java
package gift.option;

public class OptionFixture {
    public static Option option(Long id, Product product, String name, int quantity) {
        return new Option(id, product, name, quantity);
    }
}

// src/test/java/gift/wish/WishFixture.java
package gift.wish;

public class WishFixture {
    public static Wish wish(Long id, Long memberId, Product product) {
        return new Wish(id, memberId, product);
    }
}
```

#### 테스트 코드 변경 (Before → After)

```java
// Before — Reflection 사용 (OrderServiceTest)
class OrderServiceTest {

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void create() throws Exception {  // throws Exception 전파
        Member member = new Member("test@email.com");
        setId(member, 1L);            // Reflection으로 id 설정
        member.chargePoint(100000);

        Option option = new Option(createProduct(), "Tall", 100);
        setId(option, 10L);           // 또 Reflection
        ...
    }
}

// After — Fixture 사용 (OrderServiceTest)
class OrderServiceTest {

    // setId 메서드 완전 제거, throws Exception 불필요

    @Test
    void create() {  // throws Exception 제거됨
        Member member = MemberFixture.member(1L, "test@email.com");
        member.chargePoint(100000);

        Option option = OptionFixture.option(10L, createProduct(), "Tall", 100);
        ...
    }
}
```

```java
// Before — WishServiceTest
private Product createProduct() throws Exception {
    Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", CATEGORY);
    setId(product, 1L);
    return product;
}

// After — WishServiceTest
private Product createProduct() {
    return ProductFixture.product(1L, "아메리카노", 4500, "http://img.com/coffee.png", CATEGORY);
}
```

### 사용 예시

#### 새로운 테스트에서 Fixture 활용

```java
// 리뷰 기능을 추가할 때, ReviewServiceTest에서 기존 Fixture를 재활용
@Test
void createReview() {
    Member member = MemberFixture.member(1L, "reviewer@email.com");
    Product product = ProductFixture.product(5L, "라떼", 5000, "http://img.com/latte.png", CATEGORY);

    // Reflection 코드 없이 깔끔하게 테스트 객체 생성
    ReviewRequest request = new ReviewRequest(product.getId(), "맛있어요", 5);
    ...
}
```

#### 테스트 빌더 패턴으로 확장하는 경우

```java
// 필드가 많거나 다양한 조합이 필요할 때 빌더 패턴으로 전환 가능
public class MemberTestBuilder {
    private Long id = 1L;
    private String email = "default@email.com";
    private int point = 0;

    public MemberTestBuilder id(Long id) { this.id = id; return this; }
    public MemberTestBuilder email(String email) { this.email = email; return this; }
    public MemberTestBuilder point(int point) { this.point = point; return this; }

    public Member build() {
        Member member = new Member(id, email);  // protected 생성자
        if (point > 0) member.chargePoint(point);
        return member;
    }
}

// 사용 — 관심 있는 값만 지정, 나머지는 기본값
Member member = new MemberTestBuilder()
    .id(99L)
    .point(50000)
    .build();  // email은 "default@email.com"
```

### 장단점

**장점:**
- **캡슐화 보존**: Reflection으로 private 필드에 접근하지 않으므로, 엔티티의 접근 제한이 유지된다. 필드명 변경 시 컴파일 에러로 즉시 감지된다.
- **컴파일 타임 안전성**: 문자열 기반(`"id"`)이 아닌 생성자 파라미터 기반이므로, 타입 불일치나 필드명 변경을 컴파일러가 잡아준다.
- **`throws Exception` 제거**: Reflection의 checked exception이 사라져 테스트 메서드 시그니처가 깔끔해지고, 실제 비즈니스 예외만 검증할 수 있다.
- **변경 지점 최소화**: 엔티티 생성자가 변경되면 Fixture 클래스 한 곳만 수정하면 되고, 개별 테스트 코드는 변경 불필요하다.
- **성능 향상**: Reflection의 런타임 오버헤드(보안 검사 우회, 동적 메서드 호출)가 없어진다.

**단점 또는 트레이드오프:**
- **엔티티 수정 필요**: `protected` 생성자를 엔티티에 추가해야 하므로, 프로덕션 코드가 테스트를 위해 변경된다. 이것이 설계를 오염시킨다고 보는 시각도 있다.
- **Fixture 클래스 관리**: 엔티티마다 Fixture 클래스를 만들어야 하므로 파일이 늘어난다. 엔티티 생성자가 바뀌면 Fixture도 함께 수정해야 한다.
- **패키지 구조 의존**: `protected` 접근을 위해 Fixture가 엔티티와 같은 패키지에 있어야 한다. 패키지 구조가 변경되면 Fixture의 위치도 맞춰야 한다.

### 기대효과
- Reflection 관련 코드(`setId`, `Field`, `setAccessible`, `throws Exception`)가 모든 테스트에서 완전히 제거되어 테스트 코드의 가독성이 크게 향상된다.
- 엔티티 필드명이나 생성자가 변경될 때 **컴파일 에러**로 즉시 감지되므로, 런타임 실패를 방지할 수 있다.
- Fixture 클래스가 객체 생성의 단일 진실 공급원이 되어, 새로운 테스트를 작성할 때 일관된 테스트 데이터를 빠르게 만들 수 있다.

---

## 10. 메서드 추상화 수준 통일 (책임 분리)

### 키워드

| 키워드 | 무엇인가 | 왜 사용하는가 |
|--------|----------|--------------|
| 단일 추상화 수준 원칙 (SLAP) | 하나의 메서드 안에 있는 코드가 모두 **동일한 추상화 수준**에서 동작해야 한다는 원칙. 고수준 오케스트레이션과 저수준 구현이 섞이면 안 된다. | 추상화 수준이 섞이면 메서드를 읽을 때 **전체 흐름과 세부 구현을 동시에 파악**해야 한다. 고수준 단계만 나열하면 메서드가 "목차"처럼 읽혀서, 전체 흐름을 빠르게 이해하고 세부 사항은 필요할 때만 들어가볼 수 있다. |
| 단일 책임 원칙 (SRP) | 클래스나 메서드가 **하나의 변경 이유**만 가져야 한다는 원칙. 여러 책임이 섞이면 한 책임의 변경이 다른 책임에 영향을 준다. | 재고 차감 로직이 바뀌어도 포인트 차감이나 메시지 발송에 영향을 주지 않아야 한다. 책임이 분리되어 있으면 **변경의 영향 범위가 해당 메서드로 한정**되어 사이드이펙트를 줄일 수 있다. |
| 메서드 추출 (Extract Method) | 긴 메서드에서 특정 동작을 별도 메서드로 분리하는 리팩토링 기법. 원래 메서드에는 추출한 메서드의 호출만 남긴다. | 메서드가 길면 **테스트 시 모든 분기를 한 번에 검증**해야 하고, 한 부분의 변경이 다른 부분에 영향을 줄 위험이 있다. 추출하면 각 단계를 독립적으로 이해하고 테스트할 수 있다. |
| 이펙티브 자바 (Effective Java) | Joshua Bloch의 Java 베스트 프랙티스 모음집. "메서드는 한 가지 작업만 수행해야 한다"(Item 2 등), "API 설계 시 최소한의 책임을 부여하라" 등의 원칙을 제시한다. | 메서드가 여러 작업을 수행하면 **재사용이 어렵고, 이해하기 힘들고, 테스트하기 어렵다**. 한 가지 작업만 수행하는 메서드는 이름만으로 역할이 드러나고, 조합하여 더 복잡한 동작을 만들 수 있다. |

### 구현 원리

#### 문제: 하나의 메서드가 모든 세부 구현을 직접 처리

변경 전 `OrderService.create()`는 옵션 조회, 재고 차감, 포인트 차감, 주문 저장, 메시지 발송의 **"어떻게(how)"를 모두 직접** 처리하고 있었다:

```java
// Before — 추상화 수준이 섞여있는 create()
public OrderResponse create(Member member, OrderRequest request) {
    Option option = optionRepository.findById(request.optionId())    // 저수준: Repository 직접 호출
        .orElseThrow(() -> new NoSuchElementException(...));

    option.subtractQuantity(request.quantity());                     // 저수준: 재고 차감 구현
    optionRepository.save(option);                                   // 저수준: 영속화

    int price = option.getProduct().getPrice() * request.quantity(); // 저수준: 가격 계산
    member.deductPoint(price);                                       // 저수준: 포인트 차감
    memberRepository.save(member);                                   // 저수준: 영속화

    Order saved = orderRepository.save(new Order(...));              // 고수준: 주문 저장
    sendKakaoMessageIfPossible(member, saved, option);               // 고수준: 메시지 발송
    return OrderResponse.from(saved);
}
```

이 상태에서 발생하는 문제:
1. **테스트 복잡도**: 하나의 테스트에서 옵션 조회, 재고 차감, 포인트 차감, 주문 저장, 메시지 발송을 모두 Mock/검증해야 한다.
2. **가독성**: 메서드를 읽을 때 "전체 흐름"과 "Repository 호출 세부사항"을 동시에 파악해야 한다.
3. **변경 영향**: 재고 차감 방식이 바뀌면 `create()` 메서드 자체를 수정해야 하고, 다른 단계에 영향을 줄 위험이 있다.

#### 해결: 각 단계를 private 메서드로 추출

`create()`는 **"무엇을(what)"** 하는지만 보여주는 오케스트레이션 메서드가 된다.
각 단계의 **"어떻게(how)"**는 private 메서드 안에 캡슐화한다.

### 구현 코드

#### OrderService (Before → After)

```java
// After — 추상화 수준이 통일된 create()
public OrderResponse create(Member member, OrderRequest request) {
    Option option = findOption(request.optionId());          // 무엇을: 옵션 찾기
    subtractStock(option, request.quantity());                // 무엇을: 재고 차감
    deductPoint(member, option, request.quantity());          // 무엇을: 포인트 차감

    Order saved = orderRepository.save(new Order(option, member.getId(), request.quantity(), request.message()));
    sendKakaoMessageIfPossible(member, saved, option);

    return OrderResponse.from(saved);
}

// "어떻게"는 private 메서드에 캡슐화
private Option findOption(Long optionId) {
    return optionRepository.findById(optionId)
        .orElseThrow(() -> new NoSuchElementException("Option not found. id=" + optionId));
}

private void subtractStock(Option option, int quantity) {
    option.subtractQuantity(quantity);
    optionRepository.save(option);
}

private void deductPoint(Member member, Option option, int quantity) {
    int price = option.getProduct().getPrice() * quantity;
    member.deductPoint(price);
    memberRepository.save(member);
}
```

#### OptionService — 중복 조회 통합 + 검증 분리

```java
// Before — create()에 조회·검증·저장이 뒤섞임
public OptionResponse create(Long productId, OptionRequest request) {
    validateOptionName(request.name());
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new NoSuchElementException(...));  // 조회
    if (optionRepository.existsByProductIdAndName(productId, request.name())) {
        throw new IllegalArgumentException("이미 존재하는 옵션명입니다.");  // 검증
    }
    Option saved = optionRepository.save(new Option(...));
    return OptionResponse.from(saved);
}

// After — 단계별 분리
public OptionResponse create(Long productId, OptionRequest request) {
    validateOptionName(request.name());
    Product product = findProduct(productId);
    validateDuplicateName(productId, request.name());
    Option saved = optionRepository.save(new Option(product, request.name(), request.quantity()));
    return OptionResponse.from(saved);
}

public void delete(Long productId, Long optionId) {
    findProduct(productId);
    validateNotLastOption(productId);
    Option option = findOption(optionId, productId);
    optionRepository.delete(option);
}

// 3개 public 메서드에서 재사용되는 공통 조회
private Product findProduct(Long productId) {
    return productRepository.findById(productId)
        .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + productId));
}

private void validateDuplicateName(Long productId, String name) { ... }
private void validateNotLastOption(Long productId) { ... }
```

### 사용 예시

#### create()를 읽을 때의 차이

```
Before:
  "옵션을 Repository에서 찾아서... 없으면 예외를 던지고...
   수량을 빼고... save를 호출하고...
   가격을 계산하고... 포인트를 빼고... save를 호출하고..."
  → 세부 구현을 한 줄씩 따라가야 전체 흐름을 파악할 수 있다.

After:
  "옵션 찾기 → 재고 차감 → 포인트 차감 → 주문 저장 → 메시지 발송"
  → 메서드명만 읽으면 전체 흐름이 보인다. 세부 사항은 필요할 때 private 메서드를 확인하면 된다.
```

#### 재고 차감 방식이 변경될 때

```
Before: create() 내부의 41~42줄을 직접 수정 → 주변 코드에 영향 가능성

After: subtractStock() 메서드만 수정 → create()의 다른 단계에 영향 없음
  예) 재고 차감 시 이력을 남기는 요구사항이 추가되면:
  private void subtractStock(Option option, int quantity) {
      option.subtractQuantity(quantity);
      optionRepository.save(option);
      stockHistoryRepository.save(new StockHistory(option, quantity));  // 추가
  }
```

### 장단점

**장점:**
- **가독성**: public 메서드가 "목차"처럼 읽혀서, 전체 비즈니스 흐름을 한눈에 파악할 수 있다.
- **변경 격리**: 각 단계의 구현이 private 메서드에 캡슐화되어, 한 단계를 수정해도 다른 단계에 영향을 주지 않는다.
- **중복 제거**: `findProduct()`처럼 여러 public 메서드에서 반복되던 조회 로직이 하나로 통합된다.
- **테스트 의도 명확화**: 테스트에서 Mock 설정이 어떤 단계를 위한 것인지 메서드명으로 바로 알 수 있다.

**단점 또는 트레이드오프:**
- **메서드 수 증가**: private 메서드가 늘어나 클래스가 길어질 수 있다. 단계가 더 복잡해지면 별도 Service 클래스로 분리하는 것도 고려해야 한다.
- **간접 참조**: 세부 구현을 보려면 private 메서드로 이동해야 한다. 단순한 로직까지 과도하게 추출하면 오히려 가독성이 떨어질 수 있다.
- **디버깅**: 스택 트레이스가 한 단계 더 깊어진다. 하지만 메서드명이 명확하면 오히려 디버깅에 도움이 된다.

### 기대효과
- `create()` 메서드가 비즈니스 흐름의 "목차" 역할을 하여, 새로운 개발자가 코드를 처음 볼 때 전체 주문 프로세스를 빠르게 이해할 수 있다.
- 요구사항 변경 시(예: 재고 차감 정책 변경, 포인트 계산 방식 변경) 해당 private 메서드만 수정하면 되므로 변경 범위가 최소화된다.
- `OptionService`에서 3곳에 중복되던 `productRepository.findById().orElseThrow()`가 `findProduct()` 하나로 통합되어 에러 메시지 변경 시 1곳만 수정하면 된다.
