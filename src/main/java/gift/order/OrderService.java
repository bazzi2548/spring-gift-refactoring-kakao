package gift.order;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.product.Product;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final MemberRepository memberRepository;
    private final KakaoMessageClient kakaoMessageClient;

    public OrderService(
        OrderRepository orderRepository,
        OptionRepository optionRepository,
        MemberRepository memberRepository,
        KakaoMessageClient kakaoMessageClient
    ) {
        this.orderRepository = orderRepository;
        this.optionRepository = optionRepository;
        this.memberRepository = memberRepository;
        this.kakaoMessageClient = kakaoMessageClient;
    }

    public Page<OrderResponse> findByMember(Member member, Pageable pageable) {
        return orderRepository.findByMemberId(member.getId(), pageable).map(OrderResponse::from);
    }

    public OrderResponse create(Member member, OrderRequest request) {
        Option option = findOption(request.optionId());
        subtractStock(option, request.quantity());
        deductPoint(member, option, request.quantity());

        Order saved = orderRepository.save(new Order(option, member.getId(), request.quantity(), request.message()));
        sendKakaoMessageIfPossible(member, saved, option);

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
        member.deductPoint(option.calculateTotalPrice(quantity));
        memberRepository.save(member);
    }

    private void sendKakaoMessageIfPossible(Member member, Order order, Option option) {
        if (member.getKakaoAccessToken() == null) {
            return;
        }
        try {
            Product product = option.getProduct();
            kakaoMessageClient.sendToMe(member.getKakaoAccessToken(), order, product);
        } catch (Exception e) {
            log.warn("카카오 메시지 발송 실패: memberId={}, orderId={}", member.getId(), order.getId(), e);
        }
    }
}
