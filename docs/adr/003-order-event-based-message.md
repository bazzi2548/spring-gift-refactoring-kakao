# ADR-003: OrderService 카카오 메시지 발송을 이벤트 기반으로 분리

## 상태

승인됨

## 맥락

`OrderService.create()`는 하나의 `@Transactional` 안에서 다음을 수행한다:

1. 옵션 재고 차감 (DB)
2. 회원 포인트 차감 (DB)
3. 주문 저장 (DB)
4. 카카오 메시지 발송 (외부 HTTP)

4번은 `sendKakaoMessageIfPossible()`에서 try-catch로 감싸져 있어 메시지 발송 실패가 트랜잭션 롤백을 유발하지는 않는다.

```java
@Transactional
public OrderResponse create(Member member, OrderRequest request) {
    subtractStock(option, request.quantity());       // 1. DB
    deductPoint(member, option, request.quantity()); // 2. DB
    Order saved = orderRepository.save(...);         // 3. DB
    sendKakaoMessageIfPossible(member, saved, option); // 4. 외부 API (try-catch)
    return OrderResponse.from(saved);
}

private void sendKakaoMessageIfPossible(Member member, Order order, Option option) {
    if (member.getKakaoAccessToken() == null) {
        return;
    }
    try {
        kakaoMessageClient.sendToMe(member.getKakaoAccessToken(), order, option.getProduct());
    } catch (Exception e) {
        log.warn("카카오 메시지 발송 실패: memberId={}, orderId={}", member.getId(), order.getId(), e);
    }
}
```

그러나 try-catch로 롤백을 방지하는 것과 DB 커넥션 점유는 별개의 문제다.

## 변경 이유

### 1. DB 커넥션 점유

try-catch로 롤백은 방지되지만, 외부 API 응답을 기다리는 동안 DB 커넥션을 점유하는 문제는 해결되지 않는다.
타임아웃(connect 3초 + read 5초)을 걸었지만, 최악의 경우 주문 1건당 최대 8초간 커넥션을 추가로 점유한다.

### 2. 재시도 로직 확장 불가

현실적으로 메시지 발송 실패 시 재시도가 필요하다.
현재 구조(트랜잭션 내부 try-catch)에서 재시도를 추가하면:
- 트랜잭션 안에서 반복적으로 외부 API를 호출하게 됨
- 이미 커넥션을 길게 잡는데 재시도까지 포함되면 커넥션 풀 고갈 위험이 극대화됨
- 재시도 간격(backoff)을 두면 그만큼 트랜잭션이 길어짐

### 3. 책임 분리

`OrderService`는 **주문** 을 담당한다. 카카오 메시지 발송은 주문의 부수 효과(side effect)이지 핵심 책임이 아니다.
주문 생성과 알림 발송의 책임을 분리하면:
- `OrderService`는 주문 로직에만 집중
- 메시지 발송 로직은 독립적으로 변경/확장 가능 (재시도, 다른 알림 채널 추가 등)

## 고려한 대안

### 대안 1: 현행 유지 (트랜잭션 내부 try-catch)

```java
private void sendKakaoMessageIfPossible(Member member, Order order, Option option) {
    try {
        kakaoMessageClient.sendToMe(...);
    } catch (Exception e) {
        log.warn("카카오 메시지 발송 실패: ...", e);
    }
}
```

- 장점: 단순한 구조, 추가 인프라 불필요
- 단점: 외부 API 지연 시 DB 커넥션 점유, 재시도 확장 불가, 주문과 알림 책임 혼재

### 대안 2: @TransactionalEventListener로 분리

트랜잭션 커밋 후 이벤트로 메시지 발송을 분리한다.

```java
// OrderService — 이벤트 발행
eventPublisher.publishEvent(new OrderCreatedEvent(saved, member, option));

// OrderEventListener — 트랜잭션 커밋 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    kakaoMessageClient.sendToMe(...);
}
```

- 장점: 트랜잭션과 외부 호출 완전 분리, 재시도 확장 용이, 책임 분리
- 단점: 이벤트 클래스, 리스너 등 추가 구조 필요

## 결정

**대안 2 (@TransactionalEventListener)** 로 변경한다.

## 구현 방향

```java
// OrderService — 주문만 담당
@Transactional
public OrderResponse create(Member member, OrderRequest request) {
    Option option = findOption(request.optionId());
    subtractStock(option, request.quantity());
    deductPoint(member, option, request.quantity());
    Order saved = orderRepository.save(new Order(...));
    eventPublisher.publishEvent(new OrderCreatedEvent(saved, member, option));
    return OrderResponse.from(saved);
}

// OrderEventListener — 메시지 발송만 담당
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    // 트랜잭션 밖에서 실행, DB 커넥션 점유 없음
    kakaoMessageClient.sendToMe(...);
}
```

## 근거

- **커넥션 보호**: 메시지 발송이 트랜잭션 밖에서 실행되어 DB 커넥션 점유 시간이 최소화된다.
- **확장성**: 향후 재시도, 다른 알림 채널 추가 등을 리스너 레벨에서 독립적으로 처리할 수 있다.
- **책임 분리**: OrderService는 주문, OrderEventListener는 알림으로 역할이 명확해진다.
- **안전성**: `AFTER_COMMIT`이므로 주문이 확실히 저장된 후에만 메시지가 발송된다. 트랜잭션 롤백 시 메시지가 발송되지 않는다.

## 트레이드오프

- 이벤트 클래스(`OrderCreatedEvent`)와 리스너(`OrderEventListener`) 등 구조가 추가된다.
- 이벤트 리스너 실패 시 주문은 이미 커밋되었으므로 메시지 미발송 상태가 될 수 있다 (기존에도 try-catch로 동일한 상황 발생 가능했음).
