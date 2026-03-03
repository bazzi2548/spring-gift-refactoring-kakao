package gift.member;

import gift.auth.JwtProvider;
import gift.auth.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final PasswordEncoder NO_OP_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    };

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("회원 가입에 성공하면 토큰을 반환한다")
    void registerSuccess() {
        MemberRequest request = new MemberRequest("test@email.com", "password");
        Member member = new Member("test@email.com", new Password("hashed", NO_OP_ENCODER));

        given(passwordEncoder.encode("password")).willReturn("hashed");
        given(memberRepository.existsByEmail("test@email.com")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willReturn(member);
        given(jwtProvider.createToken("test@email.com")).willReturn("jwt-token");

        TokenResponse response = memberService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("이미 등록된 이메일로 가입 시 예외가 발생한다")
    void registerDuplicateEmail() {
        MemberRequest request = new MemberRequest("test@email.com", "password");

        given(memberRepository.existsByEmail("test@email.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.register(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("올바른 정보로 로그인하면 토큰을 반환한다")
    void loginSuccess() {
        MemberRequest request = new MemberRequest("test@email.com", "password");
        Member member = new Member("test@email.com", new Password("hashed", NO_OP_ENCODER));

        given(memberRepository.findByEmail("test@email.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password", "hashed")).willReturn(true);
        given(jwtProvider.createToken("test@email.com")).willReturn("jwt-token");

        TokenResponse response = memberService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 예외가 발생한다")
    void loginEmailNotFound() {
        MemberRequest request = new MemberRequest("unknown@email.com", "password");

        given(memberRepository.findByEmail("unknown@email.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.login(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외가 발생한다")
    void loginWrongPassword() {
        MemberRequest request = new MemberRequest("test@email.com", "wrong");
        Member member = new Member("test@email.com", new Password("hashed", NO_OP_ENCODER));

        given(memberRepository.findByEmail("test@email.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> memberService.login(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("패스워드가 null인 회원이 로그인 시 예외가 발생한다")
    void loginNullPassword() {
        MemberRequest request = new MemberRequest("test@email.com", "password");
        Member member = new Member("test@email.com"); // password is null

        given(memberRepository.findByEmail("test@email.com")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.login(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid email or password");
    }
}
