package gift.order;

import gift.product.Product;

public record OrderCreatedEvent(
    String accessToken,
    Order order,
    Product product
) {
}
