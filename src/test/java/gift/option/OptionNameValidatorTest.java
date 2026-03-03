package gift.option;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptionNameValidatorTest {

    @Test
    @DisplayName("유효한 옵션명은 에러가 없다")
    void validName() {
        List<String> errors = OptionNameValidator.validate("Tall 사이즈");

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("null 이름은 에러를 반환한다")
    void nullName() {
        List<String> errors = OptionNameValidator.validate(null);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("필수");
    }

    @Test
    @DisplayName("빈 문자열 이름은 에러를 반환한다")
    void blankName() {
        List<String> errors = OptionNameValidator.validate("   ");

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("필수");
    }

    @Test
    @DisplayName("50자 초과 이름은 에러를 반환한다")
    void nameTooLong() {
        String longName = "a".repeat(51);
        List<String> errors = OptionNameValidator.validate(longName);

        assertThat(errors).anyMatch(e -> e.contains("50자"));
    }

    @Test
    @DisplayName("50자 이하 이름은 통과한다")
    void nameExactlyMaxLength() {
        String name = "a".repeat(50);
        List<String> errors = OptionNameValidator.validate(name);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("허용되지 않는 특수문자가 포함되면 에러를 반환한다")
    void invalidSpecialCharacters() {
        List<String> errors = OptionNameValidator.validate("옵션!@#");

        assertThat(errors).anyMatch(e -> e.contains("특수 문자"));
    }

    @Test
    @DisplayName("허용된 특수문자는 통과한다")
    void allowedSpecialCharacters() {
        List<String> errors = OptionNameValidator.validate("옵션(A) [B]+C-D&E/F_G");

        assertThat(errors).isEmpty();
    }
}
