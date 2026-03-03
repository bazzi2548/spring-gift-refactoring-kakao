package gift.product;

import gift.category.Category;

public class ProductFixture {

    public static Product product(Long id, String name, int price, String imageUrl, Category category) {
        return new Product(id, name, price, imageUrl, category);
    }
}
