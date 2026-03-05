package gift.auth;

import gift.member.Member;
import gift.member.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class KakaoAuthTransactionTest {

    @Autowired
    private KakaoAuthService kakaoAuthService;

    @MockitoBean
    private KakaoLoginClient kakaoLoginClient;

    @Autowired
    private MemberRepository memberRepository;

    private static final String TEST_EMAIL = "kakao@test.com";

    @AfterEach
    void tearDown() {
        memberRepository.findByEmail(TEST_EMAIL)
            .ifPresent(memberRepository::delete);
    }

    @Test
    @DisplayName("신규 카카오 회원이 프록시를 경유하여 DB에 저장된다")
    void registerNewKakaoMember() {
        // given
        given(kakaoLoginClient.requestAccessToken("code"))
            .willReturn(new KakaoLoginClient.KakaoTokenResponse("kakao-token-123"));
        given(kakaoLoginClient.requestUserInfo("kakao-token-123"))
            .willReturn(new KakaoLoginClient.KakaoUserResponse(
                new KakaoLoginClient.KakaoUserResponse.KakaoAccount(TEST_EMAIL)));

        // when
        TokenResponse response = kakaoAuthService.handleCallback("code");

        // then: DB에 회원이 저장되었다면 프록시 경유 + 트랜잭션 커밋 증거
        assertThat(response.token()).isNotBlank();
        Member saved = memberRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(saved.getKakaoAccessToken()).isEqualTo("kakao-token-123");
    }

    @Test
    @DisplayName("기존 회원의 카카오 토큰이 프록시를 경유하여 업데이트된다")
    void updateExistingMemberKakaoToken() {
        // given: 기존 회원
        memberRepository.save(new Member(TEST_EMAIL));

        given(kakaoLoginClient.requestAccessToken("code"))
            .willReturn(new KakaoLoginClient.KakaoTokenResponse("new-token-456"));
        given(kakaoLoginClient.requestUserInfo("new-token-456"))
            .willReturn(new KakaoLoginClient.KakaoUserResponse(
                new KakaoLoginClient.KakaoUserResponse.KakaoAccount(TEST_EMAIL)));

        // when
        kakaoAuthService.handleCallback("code");

        // then: DB에서 토큰이 업데이트되었다면 프록시 경유 + 트랜잭션 커밋 증거
        Member updated = memberRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(updated.getKakaoAccessToken()).isEqualTo("new-token-456");
    }
}
