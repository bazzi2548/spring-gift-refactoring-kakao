# 리팩토링 계획

## 현재 상태 요약

Service 계층 없이 Controller가 모든 비즈니스 로직을 직접 처리하고 있다.
보안(평문 패스워드), 동시성(재고 차감 race condition), 일관성(에러 처리 패턴) 측면에서 개선이 필요하다.

---

## 1단계: 스타일 정리 (작동 변경 없음)

### 1-1. `.orElse(null)` 패턴 통일

모든 Controller에서 `.orElse(null)` + null 체크 패턴이 반복되고 있다.
`.orElseThrow()`로 통일한다.

**대상 파일:**
- `ProductController.java`
- `CategoryController.java`
- `WishController.java`
- `OptionController.java`
- `OrderController.java`

```java
// Before
var product = productRepository.findById(id).orElse(null);
if (product == null) {
    return ResponseEntity.notFound().build();
}

// After
var product = productRepository.findById(id)
    .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
```

### 1-2. 불필요한 `@Autowired` 제거

단일 생성자에 붙은 `@Autowired`는 Spring 4.3+에서 불필요하다.

**대상 파일:**
- `AuthenticationResolver.java`
- `JwtProvider.java`

### 1-3. `ResponseEntity<?>` 제네릭 타입 명시

**대상 파일:**
- `OrderController.java` — `ResponseEntity<?>` → `ResponseEntity<Page<OrderResponse>>`

---

## 2단계: 불필요한 코드 제거 (작동 변경 없음)

### 2-1. 중복된 `@ExceptionHandler` 제거

각 Controller에 산재한 `@ExceptionHandler`를 글로벌 핸들러로 통합한다.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

**대상 파일:**
- `MemberController.java` — `@ExceptionHandler` 제거
- `OptionController.java` — `@ExceptionHandler` 제거

### 2-2. 중복된 인증 체크 코드 제거

모든 Controller에서 반복되는 인증 패턴을 정리한다.

```java
// 현재: 매 메서드마다 반복
var member = authenticationResolver.extractMember(authorization);
if (member == null) {
    return ResponseEntity.status(401).build();
}
```

**대상 파일:**
- `WishController.java` — 3회 반복
- `OrderController.java` — 2회 반복

### 2-3. `AdminProductController` 중복 헬퍼 메서드 정리

`populateNewForm()`과 `populateEditForm()`이 거의 동일하다. 하나로 합칠 수 있는지 확인한다.

---

## 3단계: Service 계층 추출 (구조 변경, 작동 변경 없음)

### 3-1. 추출 대상

| Controller | 추출할 Service | 주요 이동 대상 로직 |
|---|---|---|
| `OrderController` | `OrderService` | 재고 차감, 포인트 차감, 주문 생성, 카카오 메시지 발송 |
| `ProductController` | `ProductService` | 상품 CRUD, 이름 검증, 카테고리 검증 |
| `CategoryController` | `CategoryService` | 카테고리 CRUD |
| `MemberController` | `MemberService` | 회원가입, 로그인, 패스워드 검증 |
| `WishController` | `WishService` | 위시 추가(중복 체크), 삭제 |
| `OptionController` | `OptionService` | 옵션 CRUD, 이름 검증, 최소 1개 규칙 |
| `KakaoAuthController` | `KakaoLoginService` | 회원 조회/생성, 토큰 갱신, JWT 발급 |

### 3-2. 추출 원칙

- Controller는 요청 검증과 위임만 담당한다.
- Service에 `@Transactional`을 붙여 트랜잭션 경계를 명확히 한다.
- 한 커밋에 하나의 Service만 추출한다.

### 3-3. 추출 순서 (의존 관계 기준)

1. `CategoryService` — 의존 없음, 가장 단순
2. `ProductService` — CategoryRepository 의존
3. `MemberService` — 의존 없음
4. `OptionService` — ProductRepository 의존
5. `WishService` — Member, Product 의존
6. `KakaoLoginService` — MemberRepository, JwtProvider 의존
7. `OrderService` — Option, Member, KakaoMessageClient 의존, 가장 복잡

---

## 이후 단계 (작동 변경 포함, 이번 step1 범위 밖)

아래는 구조 변경이 아닌 작동 변경이므로, step1에서는 수행하지 않는다.
기록만 남겨둔다.

### 보안

| 심각도 | 위치 | 내용 |
|---|---|---|
| ~~높음~~ | ~~`MemberController`, `AdminMemberController`~~ | ~~패스워드 평문 저장/비교 → BCrypt 적용~~ (해결: Password 일급객체 + BCryptPasswordEncoder) |
| 높음 | `AdminMemberController`, `AdminProductController` | `/admin/*` 인가 처리 없음 |
| ~~높음~~ | ~~`V2__Insert_default_data.sql`~~ | ~~평문 비밀번호 기본 데이터~~ (해결: V3 마이그레이션으로 BCrypt 해시 변환) |
| 중간 | `KakaoAuthController` | 카카오 액세스 토큰 평문 DB 저장 |

### 동시성

| 심각도 | 위치 | 내용 |
|---|---|---|
| 높음 | `OrderController:87-88` | 재고 차감 race condition → `@Transactional` + 낙관적 락 |

### 데이터 정합성 (DB 스키마)

| 심각도 | 위치 | 내용 |
|---|---|---|
| 중간 | `wish` 테이블 | `UNIQUE(member_id, product_id)` 제약 없음 |
| 중간 | `options` 테이블 | `UNIQUE(product_id, name)` 제약 없음 |
| 낮음 | 외래 키 전체 | `ON DELETE CASCADE/RESTRICT` 미지정 |
| 낮음 | `member.point` | `int` → `bigint` 변경 권장 |
| 낮음 | 전체 테이블 | `created_at`, `updated_at` 감사 컬럼 없음 |

### 잠재적 버그

| 심각도 | 위치 | 내용 |
|---|---|---|
| 중간 | `Option.subtractQuantity()` | 음수 amount 검증 없음 |
| 중간 | `ProductResponse.from()` | category null 시 NPE |
| 중간 | `OrderController:111` | 카카오 메시지 실패 `catch (Exception ignored)` |
| 중간 | `AuthenticationResolver:30` | 모든 예외를 `return null`로 처리 |
| 낮음 | `deleteProduct()`, `deleteCategory()` | 존재 여부 미확인 후 204 반환 |

### 테스트

- `src/test/`에 `.gitkeep`만 존재한다.
- 리팩토링 전에 기존 동작을 검증할 테스트를 작성해야 한다.
