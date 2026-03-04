package gift.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("포인트 충전에 성공하면 잔액이 증가한다")
    void chargePointSuccess() {
        Member member = MemberFixture.member(1L, "user@test.com");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.save(member)).willReturn(member);

        adminMemberService.chargePoint(1L, 5000);

        assertThat(member.getPoint()).isEqualTo(5000);
    }

    @Test
    @DisplayName("0 이하 금액 충전 시 예외가 발생하고 포인트가 변하지 않는다")
    void chargePointZeroOrNegativeFails() {
        Member member = MemberFixture.member(1L, "user@test.com");
        member.chargePoint(1000);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> adminMemberService.chargePoint(1L, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("충전 금액은 1 이상이어야 합니다");

        assertThat(member.getPoint()).isEqualTo(1000);
    }

    @Test
    @DisplayName("음수 금액 충전 시 예외가 발생하고 포인트가 변하지 않는다")
    void chargePointNegativeFails() {
        Member member = MemberFixture.member(1L, "user@test.com");
        member.chargePoint(1000);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> adminMemberService.chargePoint(1L, -500))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("충전 금액은 1 이상이어야 합니다");

        assertThat(member.getPoint()).isEqualTo(1000);
    }
}
