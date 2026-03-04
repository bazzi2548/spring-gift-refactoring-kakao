# ADR-002: KakaoAuthService 트랜잭션 분리

## 상태

승인됨

## 맥락

`KakaoAuthService.handleCallback()`은 하나의 `@Transactional` 안에서 다음을 수행했다:

1. 카카오 토큰 교환 (외부 HTTP)
2. 카카오 사용자 정보 조회 (외부 HTTP)
3. 회원 조회/생성 (DB)
4. 카카오 액세스 토큰 저장 (DB)
5. JWT 발급

### 문제 1: DB 커넥션 장시간 점유

1, 2번 외부 HTTP 호출 동안 DB 커넥션을 점유하고 있었다.
카카오 서버 지연/장애 시 커넥션 풀이 고갈될 수 있다.

### 문제 2: 도메인 책임 위치

3~5번은 "회원 등록/업데이트 후 토큰 발급"이라는 **Member 도메인의 책임**이다.
카카오든 일반 이메일이든, 회원을 등록하고 서비스 토큰을 발급하는 행위는 `MemberService`에 속해야 한다.
`KakaoAuthService`가 `MemberRepository`와 `JwtProvider`를 직접 사용하는 것은 도메인 경계를 넘는 것이었다.

## 고려한 대안

### 대안 1: KakaoAuthService 내에서 메서드 분리

외부 호출과 DB 작업을 같은 클래스의 다른 메서드로 분리한다.

- 단점: 자가참조(self-invocation) 문제로 내부 메서드의 `@Transactional`이 적용되지 않음

### 대안 2: MemberService에 카카오 회원 등록 메서드 위임

`KakaoAuthService`는 외부 API 호출만, `MemberService`는 회원 등록/업데이트 + JWT 발급을 담당한다.

- 장점: 자가참조 문제 없음, 도메인 책임이 올바른 위치로 이동

## 결정

**대안 2 (MemberService 위임)** 를 선택한다.

## 변경 내용

### Before

```java
@Service
@Transactional(readOnly = true)
public class KakaoAuthService {
    private final KakaoLoginClient kakaoLoginClient;
    private final MemberRepository memberRepository;  // 직접 접근
    private final JwtProvider jwtProvider;              // 직접 접근

    @Transactional
    public TokenResponse handleCallback(String code) {
        // 외부 API + DB 작업이 하나의 트랜잭션
        KakaoTokenResponse kakaoToken = kakaoLoginClient.requestAccessToken(code);
        KakaoUserResponse kakaoUser = kakaoLoginClient.requestUserInfo(kakaoToken.accessToken());
        Member member = memberRepository.findByEmail(email).orElseGet(...);
        member.updateKakaoAccessToken(kakaoToken.accessToken());
        memberRepository.save(member);
        return new TokenResponse(jwtProvider.createToken(member.getEmail()));
    }
}
```

### After

```java
@Service
public class KakaoAuthService {
    private final KakaoLoginClient kakaoLoginClient;
    private final MemberService memberService;  // 도메인 서비스에 위임

    public TokenResponse handleCallback(String code) {
        // 외부 API 호출 (트랜잭션 밖)
        KakaoTokenResponse kakaoToken = kakaoLoginClient.requestAccessToken(code);
        KakaoUserResponse kakaoUser = kakaoLoginClient.requestUserInfo(kakaoToken.accessToken());
        // DB 작업 (MemberService의 트랜잭션 안)
        return memberService.registerOrUpdateKakaoMember(kakaoUser.email(), kakaoToken.accessToken());
    }
}
```

## 근거

1. **DB 커넥션 보호**: 외부 HTTP 호출이 트랜잭션 밖에서 실행되어 DB 커넥션 점유 시간이 최소화된다.
2. **도메인 책임 정렬**: 회원 등록/업데이트는 `MemberService`의 책임이다. `KakaoAuthService`는 카카오 OAuth 흐름(외부 API)만 담당한다.
3. **자가참조 회피**: 별도 Service로 분리하여 Spring 프록시를 정상적으로 거친다.
