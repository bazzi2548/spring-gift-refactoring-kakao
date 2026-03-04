# ADR-002: 클래스 레벨 @Transactional(readOnly = true) 패턴

## 상태

승인됨

## 맥락

모든 Service 클래스에 `@Transactional`을 적용할 때, 메서드 레벨과 클래스 레벨 중 어느 방식을 사용할지 결정해야 한다.

### 메서드 레벨 방식

```java
@Service
public class ProductService {
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) { ... }

    @Transactional
    public ProductResponse create(ProductRequest request) { ... }
}
```

### 클래스 레벨 방식

```java
@Service
@Transactional(readOnly = true)
public class ProductService {
    public ProductResponse findById(Long id) { ... }

    @Transactional
    public ProductResponse create(ProductRequest request) { ... }
}
```

## 고려한 대안

### 대안 1: 메서드 레벨 @Transactional

모든 public 메서드에 개별적으로 `@Transactional` 또는 `@Transactional(readOnly = true)`를 선언한다.

- 장점: 각 메서드의 트랜잭션 속성이 명시적으로 보인다
- 단점: 반복적인 어노테이션, 새 메서드 추가 시 어노테이션 누락 가능성

### 대안 2: 클래스 레벨 @Transactional(readOnly = true) + CUD 메서드 오버라이드

클래스에 `@Transactional(readOnly = true)`를 기본값으로 선언하고,
쓰기 메서드(CUD)에만 `@Transactional`을 오버라이드한다.

- 장점: 새 조회 메서드 추가 시 자동으로 readOnly 트랜잭션 적용, 보일러플레이트 감소
- 단점: 클래스 레벨 어노테이션을 인지해야 함

## 결정

**대안 2 (클래스 레벨)** 를 선택한다.

## 근거

1. **엔터프라이즈 관례**: Spring 기반 엔터프라이즈 환경에서 Service의 모든 public 메서드는 트랜잭션 경계로 설계하는 것이 일반적이다.

2. **안전한 기본값**: `readOnly = true`를 기본값으로 두면, 새로 추가되는 조회 메서드가 자동으로 읽기 전용 트랜잭션을 갖는다. 쓰기 메서드에서만 명시적으로 `@Transactional`을 선언하므로, CUD 메서드를 readOnly로 잘못 남길 위험이 낮다.

3. **자가참조(self-invocation) 문제**: Spring AOP 프록시는 같은 클래스 내부의 `this.method()` 호출에 대해 @Transactional을 적용하지 못한다. 그러나 잘 설계된 Service에서는:
   - public 메서드 = 트랜잭션 경계 (외부에서 호출)
   - private 메서드 = 이미 열린 트랜잭션 내에서 실행
   - public 메서드 간 자가참조는 설계상 발생하지 않아야 한다

   따라서 클래스 레벨 @Transactional이 자가참조 문제를 유발할 현실적 위험은 낮다.

## 적용 범위

| Service | 클래스 레벨 | CUD 오버라이드 |
|---|---|---|
| `OrderService` | `@Transactional(readOnly = true)` | `create()` |
| `MemberService` | `@Transactional(readOnly = true)` | `register()`, `login()` |
| `AdminMemberService` | `@Transactional(readOnly = true)` | `createMember()`, `updateMember()`, `chargePoint()`, `deleteMember()` |
| `ProductService` | `@Transactional(readOnly = true)` | `create()`, `update()`, `delete()` |
| `CategoryService` | `@Transactional(readOnly = true)` | `create()`, `update()`, `delete()` |
| `OptionService` | `@Transactional(readOnly = true)` | `create()`, `delete()` |
| `WishService` | `@Transactional(readOnly = true)` | `add()`, `remove()` |
| `KakaoAuthService` | `@Transactional(readOnly = true)` | `handleCallback()` |

## 후속 조치

새 Service 클래스 생성 시 동일한 패턴을 따른다.
