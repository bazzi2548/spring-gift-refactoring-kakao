package gift.order;

import java.util.NoSuchElementException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
        OrderRepository orderRepository,
        OptionRepository optionRepository,
        MemberRepository memberRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.optionRepository = optionRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<OrderResponse> findByMember(Member member, Pageable pageable) {
        return orderRepository.findByMemberId(member.getId(), pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse create(Member member, OrderRequest request) {
        Option option = findOption(request.optionId());
        subtractStock(option, request.quantity());
        deductPoint(member, option, request.quantity());

        Order saved = orderRepository.save(new Order(option, member.getId(), request.quantity(), request.message()));
        publishOrderCreatedEvent(member, saved, option);

        return OrderResponse.from(saved);
    }

    private Option findOption(Long optionId) {
        return optionRepository.findById(optionId)
            .orElseThrow(() -> new NoSuchElementException("옵션이 존재하지 않습니다. id=" + optionId));
    }

    private void subtractStock(Option option, int quantity) {
        option.subtractQuantity(quantity);
        optionRepository.save(option);
    }

    private void deductPoint(Member member, Option option, int quantity) {
        member.deductPoint(option.getPrice() * quantity);
        memberRepository.save(member);
    }

    private void publishOrderCreatedEvent(Member member, Order order, Option option) {
        if (member.getKakaoAccessToken() == null) {
            return;
        }
        eventPublisher.publishEvent(new OrderCreatedEvent(member.getKakaoAccessToken(), order, option.getProduct()));
    }
}
