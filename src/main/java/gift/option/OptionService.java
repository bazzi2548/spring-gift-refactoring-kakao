package gift.option;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import gift.product.Product;
import gift.product.ProductRepository;

@Service
public class OptionService {
    private final OptionRepository optionRepository;
    private final ProductRepository productRepository;

    public OptionService(OptionRepository optionRepository, ProductRepository productRepository) {
        this.optionRepository = optionRepository;
        this.productRepository = productRepository;
    }

    public List<OptionResponse> findByProductId(Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + productId));
        return optionRepository.findByProductId(productId).stream()
            .map(OptionResponse::from)
            .toList();
    }

    public OptionResponse create(Long productId, OptionRequest request) {
        validateOptionName(request.name());

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + productId));

        if (optionRepository.existsByProductIdAndName(productId, request.name())) {
            throw new IllegalArgumentException("이미 존재하는 옵션명입니다.");
        }

        Option saved = optionRepository.save(new Option(product, request.name(), request.quantity()));
        return OptionResponse.from(saved);
    }

    public void delete(Long productId, Long optionId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + productId));

        List<Option> options = optionRepository.findByProductId(productId);
        if (options.size() <= 1) {
            throw new IllegalArgumentException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.");
        }

        Option option = optionRepository.findById(optionId)
            .filter(o -> o.getProduct().getId().equals(productId))
            .orElseThrow(() -> new NoSuchElementException("Option not found. id=" + optionId));

        optionRepository.delete(option);
    }

    private void validateOptionName(String name) {
        List<String> errors = OptionNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
}
