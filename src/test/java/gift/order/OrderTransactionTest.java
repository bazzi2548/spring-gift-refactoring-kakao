package gift.order;

import gift.category.Category;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member testMember;
    private Option testOption;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("테스트", "#000000", "http://img.com/test.png", "테스트용"));
        Product product = productRepository.save(new Product("테스트 상품", 5000, "http://img.com/product.png", category));
        testOption = optionRepository.save(new Option(product, "기본", 100));
        testMember = memberRepository.save(new Member("broke@test.com"));
    }

    @AfterEach
    void tearDown() {
        optionRepository.delete(testOption);
        productRepository.delete(testOption.getProduct());
        categoryRepository.delete(testOption.getProduct().getCategory());
        memberRepository.delete(testMember);
    }

    @Test
    @DisplayName("포인트 부족으로 주문 실패 시 차감된 재고가 롤백된다")
    void rollbackStockOnPointFailure() {
        // given: 포인트 0인 회원 + 재고 100인 옵션
        OrderRequest request = new OrderRequest(testOption.getId(), 1, "테스트");

        // when: 주문 생성 → 재고 차감 후 포인트 차감에서 실패
        assertThatThrownBy(() -> orderService.create(testMember, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("포인트가 부족합니다");

        // then: DB에서 옵션 재조회 → 재고 롤백 확인
        Option reloaded = optionRepository.findById(testOption.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(100);
    }
}
