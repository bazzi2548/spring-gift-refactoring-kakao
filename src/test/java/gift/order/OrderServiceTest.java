package gift.order;

import gift.category.Category;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OptionRepository optionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private KakaoMessageClient kakaoMessageClient;

    @InjectMocks
    private OrderService orderService;

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    @DisplayName("회원의 주문 목록을 조회한다")
    void findByMember() throws Exception {
        Member member = new Member("test@email.com");
        setId(member, 1L);

        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 100);
        setId(option, 10L);
        Order order = new Order(option, 1L, 2, "선물");

        given(orderRepository.findByMemberId(1L, PageRequest.of(0, 10)))
            .willReturn(new PageImpl<>(List.of(order)));

        Page<OrderResponse> result = orderService.findByMember(member, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문을 생성한다")
    void create() throws Exception {
        Member member = new Member("test@email.com");
        setId(member, 1L);
        member.chargePoint(100000);

        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 100);
        setId(option, 10L);

        OrderRequest request = new OrderRequest(10L, 2, "선물입니다");
        Order saved = new Order(option, 1L, 2, "선물입니다");

        given(optionRepository.findById(10L)).willReturn(Optional.of(option));
        given(optionRepository.save(any(Option.class))).willReturn(option);
        given(memberRepository.save(any(Member.class))).willReturn(member);
        given(orderRepository.save(any(Order.class))).willReturn(saved);

        OrderResponse response = orderService.create(member, request);

        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.message()).isEqualTo("선물입니다");
        assertThat(option.getQuantity()).isEqualTo(98); // 100 - 2
    }

    @Test
    @DisplayName("존재하지 않는 옵션으로 주문 시 예외가 발생한다")
    void createOptionNotFound() {
        Member member = new Member("test@email.com");
        OrderRequest request = new OrderRequest(999L, 1, "선물");

        given(optionRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(member, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("포인트 부족 시 주문 생성에 실패한다")
    void createInsufficientPoints() throws Exception {
        Member member = new Member("test@email.com");
        setId(member, 1L);
        member.chargePoint(1000); // 포인트 부족

        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 100);

        OrderRequest request = new OrderRequest(10L, 2, "선물");

        given(optionRepository.findById(10L)).willReturn(Optional.of(option));
        given(optionRepository.save(any(Option.class))).willReturn(option);

        assertThatThrownBy(() -> orderService.create(member, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("포인트가 부족합니다");
    }

    @Test
    @DisplayName("재고보다 많은 수량 주문 시 예외가 발생한다")
    void createExceedingStock() throws Exception {
        Member member = new Member("test@email.com");
        setId(member, 1L);
        member.chargePoint(100000);

        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 5);

        OrderRequest request = new OrderRequest(10L, 10, "선물");

        given(optionRepository.findById(10L)).willReturn(Optional.of(option));

        assertThatThrownBy(() -> orderService.create(member, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("차감할 수량이 현재 재고보다 많습니다");
    }
}
