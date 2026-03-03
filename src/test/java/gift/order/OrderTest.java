package gift.order;

import gift.category.Category;
import gift.option.Option;
import gift.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    @DisplayName("주문을 생성한다")
    void create() {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        Option option = new Option(product, "Tall", 100);

        Order order = new Order(option, 1L, 2, "선물입니다");

        assertThat(order.getOption()).isEqualTo(option);
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getMessage()).isEqualTo("선물입니다");
        assertThat(order.getOrderDateTime()).isNotNull();
    }
}
