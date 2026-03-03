package gift.option;

import gift.category.Category;
import gift.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionTest {

    @Test
    @DisplayName("옵션을 생성한다")
    void create() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 100);

        assertThat(option.getProduct()).isEqualTo(product);
        assertThat(option.getName()).isEqualTo("Tall");
        assertThat(option.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("수량을 차감한다")
    void subtractQuantity() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 100);

        option.subtractQuantity(30);

        assertThat(option.getQuantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("전체 수량을 차감하면 0이 된다")
    void subtractAllQuantity() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 50);

        option.subtractQuantity(50);

        assertThat(option.getQuantity()).isZero();
    }

    @Test
    @DisplayName("재고보다 많은 수량 차감 시 예외가 발생한다")
    void subtractQuantityExceedingStockThrows() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 10);

        assertThatThrownBy(() -> option.subtractQuantity(11))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("차감할 수량이 현재 재고보다 많습니다");
    }
}
