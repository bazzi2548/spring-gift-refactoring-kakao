package gift.member;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Represents a registered member.
 *
 * @author brian.kim
 * @since 1.0
 */
@Entity
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Embedded
    private Password password;

    private String kakaoAccessToken;

    private int point;

    protected Member() {
    }

    protected Member(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public Member(String email, Password password) {
        this.email = email;
        this.password = password;
    }

    public Member(String email) {
        this.email = email;
    }

    public void update(String email, Password password) {
        this.email = email;
        this.password = password;
    }

    public void updateKakaoAccessToken(String kakaoAccessToken) {
        this.kakaoAccessToken = kakaoAccessToken;
    }

    public void chargePoint(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 1 이상이어야 합니다.");
        }
        this.point += amount;
    }

    // point deduction for order payment
    public void deductPoint(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 1 이상이어야 합니다.");
        }
        if (amount > this.point) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        this.point -= amount;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean checkPassword(String rawPassword, PasswordEncoder encoder) {
        if (password == null) {
            return false;
        }
        return password.matches(rawPassword, encoder);
    }

    public String getPassword() {
        if (password == null) {
            return null;
        }
        return password.getPassword();
    }

    public String getKakaoAccessToken() {
        return kakaoAccessToken;
    }

    public int getPoint() {
        return point;
    }
}
