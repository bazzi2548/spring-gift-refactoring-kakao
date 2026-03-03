package gift.wish;

import gift.product.Product;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Wish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // primitive FK - no entity reference
    private Long memberId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    protected Wish() {
    }

    protected Wish(Long id, Long memberId, Product product) {
        this.id = id;
        this.memberId = memberId;
        this.product = product;
    }

    public Wish(Long memberId, Product product) {
        this.memberId = memberId;
        this.product = product;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Product getProduct() {
        return product;
    }
}
