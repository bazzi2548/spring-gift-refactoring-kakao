package gift.order;

import gift.member.Member;
import gift.option.Option;

public record OrderCreatedEvent(
    String accessToken,
    Long orderId,
    String productName,
    int productPrice,
    String optionName,
    int quantity,
    String message
) {
    public static OrderCreatedEvent of(Member member, Order order, Option option) {
        return new OrderCreatedEvent(
            member.getKakaoAccessToken(),
            order.getId(),
            option.getProduct().getName(),
            option.getPrice(),
            option.getName(),
            order.getQuantity(),
            order.getMessage()
        );
    }
}
