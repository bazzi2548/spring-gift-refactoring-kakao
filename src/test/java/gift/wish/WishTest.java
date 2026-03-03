package gift.wish;

import gift.category.Category;
import gift.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WishTest {

    @Test
    @DisplayName("위시를 생성한다")
    void create() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);

        Wish wish = new Wish(1L, product);

        assertThat(wish.getMemberId()).isEqualTo(1L);
        assertThat(wish.getProduct()).isEqualTo(product);
    }
}
