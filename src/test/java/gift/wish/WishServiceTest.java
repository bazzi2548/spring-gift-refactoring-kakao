package gift.wish;

import gift.auth.AuthenticationResolver;
import gift.category.Category;
import gift.member.Member;
import gift.product.Product;
import gift.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WishServiceTest {

    @Mock
    private WishRepository wishRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuthenticationResolver authenticationResolver;

    @InjectMocks
    private WishService wishService;

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private Product createProduct() throws Exception {
        Category category = new Category("교환권", "#FF0000", "http://img.com/cat.png", "설명");
        Product product = new Product("아메리카노", 4500, "http://img.com/coffee.png", category);
        setId(product, 1L);
        return product;
    }

    private Member createMember() throws Exception {
        Member member = new Member("test@email.com");
        setId(member, 1L);
        return member;
    }

    @Test
    @DisplayName("인증된 회원을 반환한다")
    void resolveMember() {
        Member member = new Member("test@email.com");
        given(authenticationResolver.extractMember("Bearer token")).willReturn(member);

        Member result = wishService.resolveMember("Bearer token");

        assertThat(result.getEmail()).isEqualTo("test@email.com");
    }

    @Test
    @DisplayName("인증 실패 시 예외가 발생한다")
    void resolveMemberUnauthorized() {
        given(authenticationResolver.extractMember("Bearer invalid")).willReturn(null);

        assertThatThrownBy(() -> wishService.resolveMember("Bearer invalid"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unauthorized");
    }

    @Test
    @DisplayName("회원의 위시리스트를 조회한다")
    void findByMember() throws Exception {
        Member member = createMember();
        Product product = createProduct();
        Wish wish = new Wish(1L, product);

        given(wishRepository.findByMemberId(1L, PageRequest.of(0, 10)))
            .willReturn(new PageImpl<>(List.of(wish)));

        Page<WishResponse> result = wishService.findByMember(member, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("위시리스트에 상품을 추가한다")
    void add() throws Exception {
        Member member = createMember();
        Product product = createProduct();
        WishRequest request = new WishRequest(1L);
        Wish saved = new Wish(1L, product);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(wishRepository.findByMemberIdAndProductId(1L, 1L)).willReturn(Optional.empty());
        given(wishRepository.save(any(Wish.class))).willReturn(saved);

        WishResponse response = wishService.add(member, request);

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("이미 위시리스트에 있는 상품을 추가하면 기존 항목을 반환한다")
    void addDuplicate() throws Exception {
        Member member = createMember();
        Product product = createProduct();
        WishRequest request = new WishRequest(1L);
        Wish existing = new Wish(1L, product);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(wishRepository.findByMemberIdAndProductId(1L, 1L)).willReturn(Optional.of(existing));

        WishResponse response = wishService.add(member, request);

        assertThat(response.productId()).isEqualTo(1L);
        then(wishRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 상품을 위시리스트에 추가 시 예외가 발생한다")
    void addProductNotFound() throws Exception {
        Member member = createMember();
        WishRequest request = new WishRequest(999L);

        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> wishService.add(member, request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("새로운 위시 여부를 확인한다 - 새 위시")
    void isNewWishTrue() throws Exception {
        Member member = createMember();

        given(wishRepository.findByMemberIdAndProductId(1L, 1L)).willReturn(Optional.empty());

        assertThat(wishService.isNewWish(member, 1L)).isTrue();
    }

    @Test
    @DisplayName("새로운 위시 여부를 확인한다 - 기존 위시")
    void isNewWishFalse() throws Exception {
        Member member = createMember();
        Product product = createProduct();
        Wish wish = new Wish(1L, product);

        given(wishRepository.findByMemberIdAndProductId(1L, 1L)).willReturn(Optional.of(wish));

        assertThat(wishService.isNewWish(member, 1L)).isFalse();
    }

    @Test
    @DisplayName("위시리스트에서 항목을 삭제한다")
    void remove() throws Exception {
        Member member = createMember();
        Product product = createProduct();
        Wish wish = new Wish(1L, product);
        setId(wish, 10L);

        given(wishRepository.findById(10L)).willReturn(Optional.of(wish));

        wishService.remove(member, 10L);

        then(wishRepository).should().delete(wish);
    }

    @Test
    @DisplayName("존재하지 않는 위시 삭제 시 예외가 발생한다")
    void removeNotFound() throws Exception {
        Member member = createMember();

        given(wishRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> wishService.remove(member, 999L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("다른 회원의 위시를 삭제 시 예외가 발생한다")
    void removeForbidden() throws Exception {
        Member member = createMember(); // id = 1L
        Product product = createProduct();
        Wish wish = new Wish(999L, product); // memberId = 999L (다른 회원)
        setId(wish, 10L);

        given(wishRepository.findById(10L)).willReturn(Optional.of(wish));

        assertThatThrownBy(() -> wishService.remove(member, 10L))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Forbidden");
    }
}
