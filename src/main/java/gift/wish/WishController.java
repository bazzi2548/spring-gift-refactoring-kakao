package gift.wish;

import gift.auth.LoginMember;
import gift.member.Member;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/wishes")
public class WishController {
    private final WishService wishService;

    public WishController(WishService wishService) {
        this.wishService = wishService;
    }

    @GetMapping
    public ResponseEntity<Page<WishResponse>> getWishes(
        @LoginMember Member member,
        Pageable pageable
    ) {
        return ResponseEntity.ok(wishService.findByMember(member, pageable));
    }

    @PostMapping
    public ResponseEntity<WishResponse> addWish(
        @LoginMember Member member,
        @Valid @RequestBody WishRequest request
    ) {
        boolean isNew = wishService.isNewWish(member, request.productId());
        WishResponse wish = wishService.add(member, request);

        if (isNew) {
            return ResponseEntity.created(URI.create("/api/wishes/" + wish.id())).body(wish);
        }
        return ResponseEntity.ok(wish);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeWish(
        @LoginMember Member member,
        @PathVariable Long id
    ) {
        wishService.remove(member, id);
        return ResponseEntity.noContent().build();
    }

}
