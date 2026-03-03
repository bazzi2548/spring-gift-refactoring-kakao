package gift.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    @DisplayName("카테고리를 생성한다")
    void create() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/exchange.png", "상품 교환권");

        assertThat(category.getName()).isEqualTo("교환권");
        assertThat(category.getColor()).isEqualTo("#FF0000");
        assertThat(category.getImageUrl()).isEqualTo("http://img.com/exchange.png");
        assertThat(category.getDescription()).isEqualTo("상품 교환권");
    }

    @Test
    @DisplayName("카테고리 정보를 수정한다")
    void update() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/old.png", "설명");

        category.update("상품권", "#00FF00", "http://img.com/new.png", "수정된 설명");

        assertThat(category.getName()).isEqualTo("상품권");
        assertThat(category.getColor()).isEqualTo("#00FF00");
        assertThat(category.getImageUrl()).isEqualTo("http://img.com/new.png");
        assertThat(category.getDescription()).isEqualTo("수정된 설명");
    }
}
