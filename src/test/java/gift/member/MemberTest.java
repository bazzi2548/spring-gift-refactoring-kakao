package gift.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

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

    private Member createMember(String email, String password) {
        return new Member(email, new Password(password, NO_OP_ENCODER));
    }

    @Test
    @DisplayName("이메일과 비밀번호로 회원을 생성한다")
    void createWithEmailAndPassword() {
        Member member = createMember("test@email.com", "password");

        assertThat(member.getEmail()).isEqualTo("test@email.com");
        assertThat(member.getPassword()).isEqualTo("password");
        assertThat(member.getPoint()).isZero();
    }

    @Test
    @DisplayName("이메일만으로 회원을 생성한다")
    void createWithEmailOnly() {
        Member member = new Member("test@email.com");

        assertThat(member.getEmail()).isEqualTo("test@email.com");
        assertThat(member.getPassword()).isNull();
    }

    @Test
    @DisplayName("회원 정보를 수정한다")
    void update() {
        Member member = createMember("old@email.com", "oldpass");

        member.update("new@email.com", new Password("newpass", NO_OP_ENCODER));

        assertThat(member.getEmail()).isEqualTo("new@email.com");
        assertThat(member.getPassword()).isEqualTo("newpass");
    }

    @Test
    @DisplayName("카카오 액세스 토큰을 갱신한다")
    void updateKakaoAccessToken() {
        Member member = createMember("test@email.com", "password");

        member.updateKakaoAccessToken("kakao-token-123");

        assertThat(member.getKakaoAccessToken()).isEqualTo("kakao-token-123");
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() {
        Member member = createMember("test@email.com", "password");

        member.chargePoint(1000);

        assertThat(member.getPoint()).isEqualTo(1000);
    }

    @Test
    @DisplayName("포인트를 여러 번 충전하면 누적된다")
    void chargePointAccumulates() {
        Member member = createMember("test@email.com", "password");

        member.chargePoint(1000);
        member.chargePoint(500);

        assertThat(member.getPoint()).isEqualTo(1500);
    }

    @Test
    @DisplayName("0 이하 금액 충전 시 예외가 발생한다")
    void chargePointWithZeroOrNegativeThrows() {
        Member member = createMember("test@email.com", "password");

        assertThatThrownBy(() -> member.chargePoint(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.chargePoint(-100))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("포인트를 차감한다")
    void deductPoint() {
        Member member = createMember("test@email.com", "password");
        member.chargePoint(1000);

        member.deductPoint(300);

        assertThat(member.getPoint()).isEqualTo(700);
    }

    @Test
    @DisplayName("보유 포인트보다 많은 금액 차감 시 예외가 발생한다")
    void deductPointExceedingBalanceThrows() {
        Member member = createMember("test@email.com", "password");
        member.chargePoint(100);

        assertThatThrownBy(() -> member.deductPoint(200))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("포인트가 부족합니다");
    }

    @Test
    @DisplayName("0 이하 금액 차감 시 예외가 발생한다")
    void deductPointWithZeroOrNegativeThrows() {
        Member member = createMember("test@email.com", "password");

        assertThatThrownBy(() -> member.deductPoint(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.deductPoint(-100))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
