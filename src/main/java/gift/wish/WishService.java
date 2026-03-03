package gift.wish;

import gift.member.Member;
import gift.product.Product;
import gift.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class WishService {
    private final WishRepository wishRepository;
    private final ProductRepository productRepository;

    public WishService(
        WishRepository wishRepository,
        ProductRepository productRepository
    ) {
        this.wishRepository = wishRepository;
        this.productRepository = productRepository;
    }

    public Page<WishResponse> findByMember(Member member, Pageable pageable) {
        return wishRepository.findByMemberId(member.getId(), pageable).map(WishResponse::from);
    }

    public WishResponse add(Member member, WishRequest request) {
        Product product = productRepository.findById(request.productId())
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + request.productId()));

        return wishRepository.findByMemberIdAndProductId(member.getId(), product.getId())
            .map(WishResponse::from)
            .orElseGet(() -> {
                Wish saved = wishRepository.save(new Wish(member.getId(), product));
                return WishResponse.from(saved);
            });
    }

    public boolean isNewWish(Member member, Long productId) {
        return wishRepository.findByMemberIdAndProductId(member.getId(), productId).isEmpty();
    }

    public void remove(Member member, Long wishId) {
        Wish wish = wishRepository.findById(wishId)
            .orElseThrow(() -> new NoSuchElementException("Wish not found. id=" + wishId));

        if (!wish.getMemberId().equals(member.getId())) {
            throw new SecurityException("Forbidden");
        }

        wishRepository.delete(wish);
    }
}
