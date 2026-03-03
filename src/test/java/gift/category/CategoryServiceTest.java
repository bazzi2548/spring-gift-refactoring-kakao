package gift.category;

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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("모든 카테고리를 조회한다")
    void findAll() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        given(categoryRepository.findAll()).willReturn(List.of(category));

        List<CategoryResponse> result = categoryService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("교환권");
    }

    @Test
    @DisplayName("카테고리를 생성한다")
    void create() {
        CategoryRequest request = new CategoryRequest("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Category saved = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");

        given(categoryRepository.save(any(Category.class))).willReturn(saved);

        CategoryResponse response = categoryService.create(request);

        assertThat(response.name()).isEqualTo("교환권");
        assertThat(response.color()).isEqualTo("#FF0000");
    }

    @Test
    @DisplayName("카테고리를 수정한다")
    void update() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/old.png", "이전");
        CategoryRequest request = new CategoryRequest("상품권", "#00FF00", "http://img.com/new.png", "이후");

        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(categoryRepository.save(any(Category.class))).willReturn(category);

        CategoryResponse response = categoryService.update(1L, request);

        assertThat(response.name()).isEqualTo("상품권");
        assertThat(response.color()).isEqualTo("#00FF00");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 수정 시 예외가 발생한다")
    void updateNotFound() {
        CategoryRequest request = new CategoryRequest("상품권", "#00FF00", "http://img.com/new.png", "이후");

        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(999L, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("카테고리를 삭제한다")
    void delete() {
        categoryService.delete(1L);

        then(categoryRepository).should().deleteById(1L);
    }
}
