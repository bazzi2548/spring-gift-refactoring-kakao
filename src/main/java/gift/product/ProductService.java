package gift.product;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gift.category.Category;
import gift.category.CategoryRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(findProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        validateProductName(request.name());
        Category category = findCategory(request.categoryId());
        Product saved = productRepository.save(request.toEntity(category));
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        validateProductName(request.name());
        Category category = findCategory(request.categoryId());
        Product product = findProduct(id);
        product.update(request.name(), request.price(), request.imageUrl(), category);
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + categoryId));
    }

    private void validateProductName(String name) {
        List<String> errors = ProductNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
}
