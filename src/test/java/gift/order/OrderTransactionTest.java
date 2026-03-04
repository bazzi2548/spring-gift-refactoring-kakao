package gift.order;

import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderTransactionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member testMember;

    @AfterEach
    void tearDown() {
        if (testMember != null) {
            memberRepository.delete(testMember);
        }
    }

    @Test
    @DisplayName("포인트 부족으로 주문 실패 시 차감된 재고가 롤백된다")
    void rollbackStockOnPointFailure() {
        // given: 포인트 0인 회원
        testMember = memberRepository.save(new Member("broke@test.com"));

        // given: 기존 옵션의 재고 확인
        Option option = optionRepository.findById(1L).orElseThrow();
        int originalQuantity = option.getQuantity();

        OrderRequest request = new OrderRequest(option.getId(), 1, "테스트");

        // when: 주문 생성 → 재고 차감 후 포인트 차감에서 실패
        assertThatThrownBy(() -> orderService.create(testMember, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("포인트가 부족합니다");

        // then: DB에서 옵션 재조회 → 재고 롤백 확인
        Option reloaded = optionRepository.findById(option.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(originalQuantity);
    }
}
