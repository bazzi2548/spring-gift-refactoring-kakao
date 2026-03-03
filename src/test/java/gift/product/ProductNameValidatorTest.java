package gift.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNameValidatorTest {

    @Test
    @DisplayName("유효한 상품명은 에러가 없다")
    void validName() {
        List<String> errors = ProductNameValidator.validate("아이스 아메리카노");

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("null 이름은 에러를 반환한다")
    void nullName() {
        List<String> errors = ProductNameValidator.validate(null);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("필수");
    }

    @Test
    @DisplayName("빈 문자열 이름은 에러를 반환한다")
    void blankName() {
        List<String> errors = ProductNameValidator.validate("   ");

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("필수");
    }

    @Test
    @DisplayName("15자 초과 이름은 에러를 반환한다")
    void nameTooLong() {
        String longName = "a".repeat(16);
        List<String> errors = ProductNameValidator.validate(longName);

        assertThat(errors).anyMatch(e -> e.contains("15자"));
    }

    @Test
    @DisplayName("15자 이하 이름은 통과한다")
    void nameExactlyMaxLength() {
        String name = "a".repeat(15);
        List<String> errors = ProductNameValidator.validate(name);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함되면 에러를 반환한다")
    void invalidSpecialCharacters() {
        List<String> errors = ProductNameValidator.validate("상품!@#");

        assertThat(errors).anyMatch(e -> e.contains("특수 문자"));
    }

    @Test
    @DisplayName("허용된 특수문자는 통과한다")
    void allowedSpecialCharacters() {
        List<String> errors = ProductNameValidator.validate("상품(A) [B]+C-D");

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("카카오가 포함된 이름은 기본적으로 에러를 반환한다")
    void nameContainingKakaoBlocked() {
        List<String> errors = ProductNameValidator.validate("카카오 선물");

        assertThat(errors).anyMatch(e -> e.contains("카카오"));
    }

    @Test
    @DisplayName("allowKakao=true이면 카카오가 포함된 이름이 통과한다")
    void nameContainingKakaoAllowedWithFlag() {
        List<String> errors = ProductNameValidator.validate("카카오 선물", true);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("여러 규칙 위반 시 모든 에러를 반환한다")
    void multipleViolations() {
        String name = "카카오!@#$%^&*()123456";
        List<String> errors = ProductNameValidator.validate(name);

        assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
    }
}
