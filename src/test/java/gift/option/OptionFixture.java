package gift.option;

import gift.product.Product;

public class OptionFixture {

    public static Option option(Long id, Product product, String name, int quantity) {
        return new Option(id, product, name, quantity);
    }
}
