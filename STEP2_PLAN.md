# Step 2 - 리팩터링 완성하기 구현 계획

## Context

Step 1에서 Service 계층 추출, 테스트 작성, BCrypt 암호화 등을 완료했다.
하지만 REFACTOR.md에 기록했던 의도들이 아직 구현되지 않았다:
- `@Transactional` 미적용 (REFACTOR.md line 116: "Service에 @Transactional을 붙여 트랜잭션 경계를 명확히 한다")
- `Option.subtractQuantity()` 음수 검증 없음 (REFACTOR.md line 165)
- `AdminMemberController`가 Service 없이 직접 Repository 접근

Step 2의 목표는 이 누락된 작동을 완성하고, 도메인 책임을 올바른 위치로 이동하는 것이다.

---

## 커밋 계획 (7개, 구조 우선 → 작동 후행)

### Commit 1: [구조] 예외 메시지를 한글로 통일

영어로 작성된 예외 메시지를 모두 한글로 변경하여 일관성을 확보한다.
테스트의 메시지 검증도 함께 동기화한다.

---

### Commit 2: [구조] 가격 계산 로직을 Option 도메인으로 이동

**도메인 책임 되찾기 #1: 중복 제거 + 호출부 단순화**

`option.getProduct().getPrice() * quantity` 계산이 2곳에 중복:
- `OrderService.deductPoint()` (line 63)
- `KakaoMessageClient.buildTemplate()` (line 32)

| 파일 | 변경 |
|---|---|
| `Option.java` | `calculateTotalPrice(int quantity)` 메서드 추가 |
| `OrderService.java` | `deductPoint()`에서 `option.calculateTotalPrice(quantity)` 호출로 변경 |
| `KakaoMessageClient.java` | `product.getPrice() * order.getQuantity()` → `order.getOption().calculateTotalPrice(order.getQuantity())` |
| `OptionTest.java` | `calculateTotalPrice()` 단위 테스트 추가 |

개선 효과:
- 가격 계산 중복 2곳 → 1곳으로 통합
- Law of Demeter 위반 해소 (`option.getProduct().getPrice()` 제거)

---

### Commit 3: [구조] Option.subtractQuantity() 음수/0 방어 로직 추가

REFACTOR.md에 기록된 누락 작동. `Member.deductPoint()`에는 `amount <= 0` 검증이 있지만 `Option`에는 없다.

| 파일 | 변경 |
|---|---|
| `Option.java` | `subtractQuantity()` 시작부에 `if (amount <= 0) throw IllegalArgumentException` 추가 |
| `OptionTest.java` | `subtractQuantity(0)`, `subtractQuantity(-5)` → 예외 발생 + **수량 변화 없음** 검증 |

---

### Commit 4: [작동] 모든 Service에 @Transactional 추가

트랜잭션 경계를 선언하여 롤백 동작을 추가한다.

| 파일 | 변경 내용 |
|---|---|
| `OrderService.java` | `create()` → `@Transactional`, `findByMember()` → `@Transactional(readOnly=true)` |
| `MemberService.java` | `register()`, `login()` → `@Transactional` |
| `AdminMemberService.java` | CUD → `@Transactional`, R → `@Transactional(readOnly=true)` |
| `ProductService.java` | CUD → `@Transactional`, R → `@Transactional(readOnly=true)` |
| `CategoryService.java` | CUD → `@Transactional`, R → `@Transactional(readOnly=true)` |
| `OptionService.java` | CUD → `@Transactional`, R → `@Transactional(readOnly=true)` |
| `WishService.java` | CUD → `@Transactional`, R → `@Transactional(readOnly=true)` |
| `KakaoAuthService.java` | `handleCallback()` → `@Transactional` |

검증: 기존 테스트 전체 통과 (`./gradlew test`)

---

### Commit 5: [작동] 주문 실패 시 트랜잭션 롤백 검증 테스트

`OrderService.create()`에서 포인트 부족으로 실패했을 때, 이미 차감된 재고가 롤백되는지 검증한다.

**새 파일:** `src/test/java/gift/order/OrderTransactionTest.java` (`@SpringBootTest` + H2)
- 포인트 0인 회원 + 재고 100인 옵션 준비
- `orderService.create()` 호출 → `IllegalArgumentException` 발생
- **DB에서 옵션 재조회** → `quantity == 100` (롤백 확인)
- `@Transactional` 없었으면 재고가 99로 남아있었을 것 → 이것이 작동 변경의 증거

---

### Commit 6: [작동] chargePoint 서비스 경유 검증 테스트

chargePoint가 AdminMemberService를 경유하게 된 작동 변경의 증거.

| 파일 | 변경 |
|---|---|
| `AdminMemberServiceTest.java` | `chargePoint` 성공: 포인트 잔액 증가 **상태 검증** |
| `AdminMemberServiceTest.java` | `chargePoint` 실패 (0 이하 금액): 예외 + **포인트 변화 없음** 검증 |

---

### Commit 6: [작동] calculateTotalPrice, subtractQuantity 단위 테스트 보강

Commit 1, 2에서 추가한 도메인 메서드의 작동 증거를 보강한다.

| 파일 | 변경 |
|---|---|
| `OptionTest.java` | `calculateTotalPrice()` 정상 계산 검증 |
| `OptionTest.java` | `subtractQuantity(0)`, `subtractQuantity(-5)` → 예외 + 수량 불변 검증 |

---

## 요구사항 매핑

| 요구사항 | 커밋 | 증거 |
|---|---|---|
| 도메인 책임 되찾기 ≥2개 | Commit 1 (가격계산 이동) + 기완료 (Admin→AdminMemberService) | 테스트 통과 + 중복 제거 |
| 누락된 작동 구현 | Commit 2 (Option 음수 방어) + Commit 6 (테스트) | 예외 + 수량 불변 검증 |
| 트랜잭션 경계 세우기 | Commit 3 (작동) + Commit 4 (작동) | DB 재조회로 롤백 확인 |

## 검증 방법

각 커밋마다 `./gradlew test` 실행하여 전체 테스트 통과 확인.
