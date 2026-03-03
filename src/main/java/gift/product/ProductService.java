package gift.product;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import gift.category.Category;
import gift.category.CategoryRepository;

@Service
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
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + id));
        return ProductResponse.from(product);
    }

    public ProductResponse create(ProductRequest request) {
        validateProductName(request.name());

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new NoSuchElementException("Category not found. id=" + request.categoryId()));

        Product saved = productRepository.save(request.toEntity(category));
        return ProductResponse.from(saved);
    }

    public ProductResponse update(Long id, ProductRequest request) {
        validateProductName(request.name());

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new NoSuchElementException("Category not found. id=" + request.categoryId()));

        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + id));

        product.update(request.name(), request.price(), request.imageUrl(), category);
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    private void validateProductName(String name) {
        List<String> errors = ProductNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
}
