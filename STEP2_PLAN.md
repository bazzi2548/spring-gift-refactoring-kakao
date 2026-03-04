# Step 2 - 리팩터링 완성하기 구현 계획

## Context

Step 1에서 Service 계층 추출, 테스트 작성, BCrypt 암호화 등을 완료했다.
하지만 REFACTOR.md에 기록했던 의도들이 아직 구현되지 않았다:
- `@Transactional` 미적용 (REFACTOR.md line 116: "Service에 @Transactional을 붙여 트랜잭션 경계를 명확히 한다")
- `Option.subtractQuantity()` 음수 검증 없음 (REFACTOR.md line 165)
- `AdminMemberController`가 Service 없이 직접 Repository 접근

Step 2의 목표는 이 누락된 작동을 완성하고, 도메인 책임을 올바른 위치로 이동하는 것이다.

---

## 커밋 계획 (구조 우선 → 작동 후행)

### Commit 1: [구조] 예외 메시지를 한글로 통일 ✅ 완료

영어로 작성된 예외 메시지를 모두 한글로 변경하여 일관성을 확보한다.
테스트의 메시지 검증도 함께 동기화한다.

---

### Commit 2: [구조] 가격 계산 로직을 Option 도메인으로 이동 ✅ 완료

**도메인 책임 되찾기 #1: 중복 제거 + 호출부 단순화**

| 파일 | 변경 |
|---|---|
| `Option.java` | `calculateTotalPrice(int quantity)` 메서드 추가 |
| `OrderService.java` | `deductPoint()`에서 `option.calculateTotalPrice(quantity)` 호출로 변경 |
| `OptionTest.java` | `calculateTotalPrice()` 단위 테스트 추가 |

참고: `KakaoMessageClient.buildTemplate()`의 가격 계산은 표시용 로직이므로 별도 유지.

---

### Commit 3: [구조] Option.subtractQuantity() 음수/0 방어 로직 추가 ✅ 완료

| 파일 | 변경 |
|---|---|
| `Option.java` | `subtractQuantity()` 시작부에 `if (amount <= 0) throw IllegalArgumentException` 추가 |
| `OptionTest.java` | `subtractQuantity(0)`, `subtractQuantity(-5)` → 예외 발생 + **수량 변화 없음** 검증 |

---

### Commit 4: [작동] 모든 Service에 클래스 레벨 @Transactional(readOnly = true) 적용 ✅ 완료

클래스 레벨 `@Transactional(readOnly = true)` + CUD 메서드에 `@Transactional` 오버라이드 패턴 적용.
도메인별로 분리 커밋. ADR-001에 결정 근거 기록.

대상: `OrderService`, `MemberService`, `AdminMemberService`, `ProductService`, `CategoryService`, `OptionService`, `WishService`, `KakaoAuthService`

---

### Commit 5: [작동] 주문 실패 시 트랜잭션 롤백 검증 테스트 ✅ 완료

`OrderService.create()`에서 포인트 부족으로 실패했을 때, 이미 차감된 재고가 롤백되는지 검증한다.

**새 파일:** `src/test/java/gift/order/OrderTransactionTest.java` (`@SpringBootTest` + H2)
- 포인트 0인 회원 + 기존 옵션 준비
- `orderService.create()` 호출 → `IllegalArgumentException` 발생
- **DB에서 옵션 재조회** → 재고 변화 없음 (롤백 확인)
- `@AfterEach`로 테스트 데이터 정리 (테스트 격리)

---

### Commit 6: [작동] 외부 API 타임아웃 추가 ✅ 완료

외부 카카오 API 호출 시 무한 대기 방지를 위해 타임아웃을 설정한다.

| 파일 | 변경 |
|---|---|
| `KakaoLoginClient.java` | `SimpleClientHttpRequestFactory`로 connect 3초, read 5초 타임아웃 |
| `KakaoMessageClient.java` | `SimpleClientHttpRequestFactory`로 connect 3초, read 5초 타임아웃 |

---

### Commit 7: [작동] KakaoAuthService 트랜잭션 분리 ✅ 완료

`handleCallback()`에서 외부 API 호출(토큰 교환, 사용자 정보 조회)이 `@Transactional` 범위 안에 있어 DB 커넥션을 장시간 점유하는 문제를 해결한다.

| 파일 | 변경 |
|---|---|
| `KakaoAuthService.java` | 외부 API 호출을 트랜잭션 밖으로 분리 |
| `MemberService.java` | 카카오 회원 등록/업데이트 메서드 추가 (자가참조 방지) |

---

### Commit 8: [작동] chargePoint 서비스 경유 검증 테스트 ✅ 완료

chargePoint가 AdminMemberService를 경유하게 된 작동 변경의 증거.

| 파일 | 변경 |
|---|---|
| `AdminMemberServiceTest.java` | `chargePoint` 성공: 포인트 잔액 증가 **상태 검증** |
| `AdminMemberServiceTest.java` | `chargePoint` 실패 (0 이하 금액): 예외 + **포인트 변화 없음** 검증 |

---

### Commit 9: [작동] OrderService 외부 API 호출을 이벤트 기반으로 분리

`OrderService.create()`의 `@Transactional` 안에서 카카오 메시지를 발송하고 있다.
try-catch로 격리되어 있지만, 외부 API 지연 시 DB 커넥션을 점유하는 문제는 동일하다.
`@TransactionalEventListener`를 사용하여 트랜잭션 커밋 후 메시지를 발송하도록 변경한다.

```java
// Before: 트랜잭션 안에서 외부 API 호출
@Transactional
public OrderResponse create(...) {
    subtractStock(option, request.quantity());
    deductPoint(member, option, request.quantity());
    Order saved = orderRepository.save(...);
    sendKakaoMessageIfPossible(member, saved, option); // 외부 API (try-catch)
    return OrderResponse.from(saved);
}

// After: 트랜잭션 커밋 후 이벤트로 메시지 발송
@Transactional
public OrderResponse create(...) {
    subtractStock(option, request.quantity());
    deductPoint(member, option, request.quantity());
    Order saved = orderRepository.save(...);
    eventPublisher.publishEvent(new OrderCreatedEvent(...)); // 이벤트 발행
    return OrderResponse.from(saved);
}

@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    kakaoMessageClient.sendToMe(...); // 트랜잭션 밖에서 실행
}
```

| 파일 | 변경 |
|---|---|
| `OrderService.java` | 메시지 발송을 이벤트 발행으로 변경 |
| `OrderCreatedEvent.java` | 이벤트 클래스 생성 |
| `OrderEventListener.java` | `@TransactionalEventListener`로 메시지 발송 처리 |
| `docs/adr/099-transaction-boundary-order-service.md` | 이벤트 기반 분리 결정 기록 |

---

## 요구사항 매핑

| 요구사항 | 커밋 | 증거 |
|---|---|---|
| 도메인 책임 되찾기 ≥2개 | Commit 2 (가격계산 이동) + 기완료 (Admin→AdminMemberService) | 테스트 통과 + 중복 제거 |
| 누락된 작동 구현 | Commit 3 (Option 음수 방어) | 예외 + 수량 불변 검증 |
| 트랜잭션 경계 세우기 | Commit 4 (클래스 레벨) + Commit 5 (롤백 테스트) | DB 재조회로 롤백 확인 |
| 외부 API 안정성 | Commit 6 (타임아웃) + Commit 7 (KakaoAuth 분리) + Commit 9 (OrderService 이벤트 분리) | DB 커넥션 점유 최소화 |

## 검증 방법

각 커밋마다 `./gradlew test` 실행하여 전체 테스트 통과 확인.
