package gift.product;

import gift.category.Category;
import gift.category.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Category createCategory() {
        return new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
    }

    @Test
    @DisplayName("전체 상품을 페이지로 조회한다")
    void findAll() {
        Category category = createCategory();
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Pageable pageable = PageRequest.of(0, 10);

        given(productRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(product)));

        Page<ProductResponse> result = productService.findAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("ID로 상품을 조회한다")
    void findById() {
        Category category = createCategory();
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        ProductResponse response = productService.findById(1L);

        assertThat(response.name()).isEqualTo("아메리카노");
        assertThat(response.price()).isEqualTo(4500);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 예외가 발생한다")
    void findByIdNotFound() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(999L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("상품을 생성한다")
    void create() {
        Category category = createCategory();
        ProductRequest request = new ProductRequest("아메리카노", 4500, "http://img.com/coffee.png", 1L);
        Product saved = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.save(any(Product.class))).willReturn(saved);

        ProductResponse response = productService.create(request);

        assertThat(response.name()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("유효하지 않은 상품명으로 생성 시 예외가 발생한다")
    void createInvalidName() {
        ProductRequest request = new ProductRequest("카카오 상품", 4500, "http://img.com/coffee.png", 1L);

        assertThatThrownBy(() -> productService.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("카카오");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 상품 생성 시 예외가 발생한다")
    void createCategoryNotFound() {
        ProductRequest request = new ProductRequest("아메리카노", 4500, "http://img.com/coffee.png", 999L);

        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("상품을 수정한다")
    void update() {
        Category category = createCategory();
        Product product = new Product("아메리카노", 4500, "http://img.com/old.png", category);
        ProductRequest request = new ProductRequest("카페 라떼", 5000, "http://img.com/latte.png", 1L);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willReturn(product);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.name()).isEqualTo("카페 라떼");
        assertThat(response.price()).isEqualTo(5000);
    }

    @Test
    @DisplayName("존재하지 않는 상품 수정 시 예외가 발생한다")
    void updateProductNotFound() {
        Category category = createCategory();
        ProductRequest request = new ProductRequest("카페 라떼", 5000, "http://img.com/latte.png", 1L);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(999L, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("상품을 삭제한다")
    void delete() {
        productService.delete(1L);

        then(productRepository).should().deleteById(1L);
    }
}
