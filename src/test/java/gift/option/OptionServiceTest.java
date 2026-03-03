package gift.option;

import gift.category.Category;
import gift.product.Product;
import gift.product.ProductFixture;
import gift.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OptionServiceTest {

    @Mock
    private OptionRepository optionRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OptionService optionService;

    private static final Category CATEGORY = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");

    private Product createProduct() {
        return ProductFixture.product(1L, "아메리카노", 4500, "http://img.com/coffee.png", CATEGORY);
    }

    @Test
    @DisplayName("상품 ID로 옵션 목록을 조회한다")
    void findByProductId() {
        Product product = createProduct();
        Option option = new Option(product, "Tall", 100);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(optionRepository.findByProductId(1L)).willReturn(List.of(option));

        List<OptionResponse> result = optionService.findByProductId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Tall");
    }

    @Test
    @DisplayName("존재하지 않는 상품의 옵션 조회 시 예외가 발생한다")
    void findByProductIdNotFound() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> optionService.findByProductId(999L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("옵션을 생성한다")
    void create() {
        Product product = createProduct();
        OptionRequest request = new OptionRequest("Tall", 100);
        Option saved = new Option(product, "Tall", 100);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(optionRepository.existsByProductIdAndName(1L, "Tall")).willReturn(false);
        given(optionRepository.save(any(Option.class))).willReturn(saved);

        OptionResponse response = optionService.create(1L, request);

        assertThat(response.name()).isEqualTo("Tall");
        assertThat(response.quantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("이미 존재하는 옵션명으로 생성 시 예외가 발생한다")
    void createDuplicateName() {
        Product product = createProduct();
        OptionRequest request = new OptionRequest("Tall", 100);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(optionRepository.existsByProductIdAndName(1L, "Tall")).willReturn(true);

        assertThatThrownBy(() -> optionService.create(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미 존재하는 옵션명");
    }

    @Test
    @DisplayName("유효하지 않은 옵션명으로 생성 시 예외가 발생한다")
    void createInvalidName() {
        OptionRequest request = new OptionRequest("옵션!@#", 100);

        assertThatThrownBy(() -> optionService.create(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("특수 문자");
    }

    @Test
    @DisplayName("옵션을 삭제한다")
    void delete() {
        Product product = createProduct();
        Option option1 = new Option(product, "Tall", 100);
        Option option2 = new Option(product, "Grande", 50);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(optionRepository.findByProductId(1L)).willReturn(List.of(option1, option2));
        given(optionRepository.findById(10L)).willReturn(Optional.of(option1));

        optionService.delete(1L, 10L);

        then(optionRepository).should().delete(option1);
    }

    @Test
    @DisplayName("옵션이 1개뿐인 상품에서 삭제 시 예외가 발생한다")
    void deleteLastOptionThrows() {
        Product product = createProduct();
        Option option = new Option(product, "Tall", 100);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(optionRepository.findByProductId(1L)).willReturn(List.of(option));

        assertThatThrownBy(() -> optionService.delete(1L, 10L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다");
    }
}
