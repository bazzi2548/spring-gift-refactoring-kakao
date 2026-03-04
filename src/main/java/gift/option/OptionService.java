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
        findProduct(productId);
        return optionRepository.findByProductId(productId).stream()
            .map(OptionResponse::from)
            .toList();
    }

    public OptionResponse create(Long productId, OptionRequest request) {
        validateOptionName(request.name());
        Product product = findProduct(productId);
        validateDuplicateName(productId, request.name());
        Option saved = optionRepository.save(new Option(product, request.name(), request.quantity()));
        return OptionResponse.from(saved);
    }

    public void delete(Long productId, Long optionId) {
        findProduct(productId);
        validateNotLastOption(productId);
        Option option = findOption(optionId, productId);
        optionRepository.delete(option);
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + productId));
    }

    private Option findOption(Long optionId, Long productId) {
        return optionRepository.findById(optionId)
            .filter(o -> o.getProduct().getId().equals(productId))
            .orElseThrow(() -> new NoSuchElementException("옵션이 존재하지 않습니다. id=" + optionId));
    }

    private void validateDuplicateName(Long productId, String name) {
        if (optionRepository.existsByProductIdAndName(productId, name)) {
            throw new IllegalArgumentException("이미 존재하는 옵션명입니다.");
        }
    }

    private void validateNotLastOption(Long productId) {
        List<Option> options = optionRepository.findByProductId(productId);
        if (options.size() <= 1) {
            throw new IllegalArgumentException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.");
        }
    }

    private void validateOptionName(String name) {
        List<String> errors = OptionNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
}
