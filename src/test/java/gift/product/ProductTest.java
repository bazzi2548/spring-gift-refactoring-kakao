package gift.product;

import gift.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    @DisplayName("상품을 생성한다")
    void create() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아이스 아메리카노", 4500, "http://img.com/coffee.png", category);

        assertThat(product.getName()).isEqualTo("아이스 아메리카노");
        assertThat(product.getPrice()).isEqualTo(4500);
        assertThat(product.getImageUrl()).isEqualTo("http://img.com/coffee.png");
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("상품 정보를 수정한다")
    void update() {
        Category oldCategory = new Category("교환권", "#FF0000", "http://img.com/old.png", "이전");
        Category newCategory = new Category("상품권", "#00FF00", "http://img.com/new.png", "이후");
        Product product = new Product("아이스 아메리카노", 4500, "http://img.com/old.png", oldCategory);

        product.update("카페 라떼", 5000, "http://img.com/latte.png", newCategory);

        assertThat(product.getName()).isEqualTo("카페 라떼");
        assertThat(product.getPrice()).isEqualTo(5000);
        assertThat(product.getImageUrl()).isEqualTo("http://img.com/latte.png");
        assertThat(product.getCategory()).isEqualTo(newCategory);
    }
}
