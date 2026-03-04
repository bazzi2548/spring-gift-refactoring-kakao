package gift.auth;

import gift.member.MemberService;
import org.springframework.stereotype.Service;

@Service
public class KakaoAuthService {
    private final KakaoLoginClient kakaoLoginClient;
    private final MemberService memberService;

    public KakaoAuthService(
        KakaoLoginClient kakaoLoginClient,
        MemberService memberService
    ) {
        this.kakaoLoginClient = kakaoLoginClient;
        this.memberService = memberService;
    }

    public TokenResponse handleCallback(String code) {
        KakaoLoginClient.KakaoTokenResponse kakaoToken = kakaoLoginClient.requestAccessToken(code);
        KakaoLoginClient.KakaoUserResponse kakaoUser = kakaoLoginClient.requestUserInfo(kakaoToken.accessToken());

        return memberService.registerOrUpdateKakaoMember(kakaoUser.email(), kakaoToken.accessToken());
    }
}
