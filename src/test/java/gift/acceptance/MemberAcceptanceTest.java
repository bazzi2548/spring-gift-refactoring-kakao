package gift.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("회원가입에 성공한다")
    void register() {
        given()
            .contentType("application/json")
            .body("{\"email\":\"newmember@example.com\",\"password\":\"pass1234\"}")
        .when()
            .post("/api/members/register")
        .then()
            .statusCode(201)
            .body("token", notNullValue());
    }

    @Test
    @DisplayName("로그인에 성공한다")
    void login() {
        given()
            .contentType("application/json")
            .body("{\"email\":\"user1@example.com\",\"password\":\"password1\"}")
        .when()
            .post("/api/members/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue());
    }

    @Test
    @DisplayName("중복 이메일 가입 시 400")
    void registerDuplicateEmail() {
        given()
            .contentType("application/json")
            .body("{\"email\":\"admin@example.com\",\"password\":\"anypass\"}")
        .when()
            .post("/api/members/register")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("잘못된 비밀번호 시 400")
    void loginWithWrongPassword() {
        given()
            .contentType("application/json")
            .body("{\"email\":\"user1@example.com\",\"password\":\"wrongpw\"}")
        .when()
            .post("/api/members/login")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("존재하지 않는 이메일 시 400")
    void loginWithNonExistentEmail() {
        given()
            .contentType("application/json")
            .body("{\"email\":\"nonexistent@example.com\",\"password\":\"anypass\"}")
        .when()
            .post("/api/members/login")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("잘못된 이메일 형식 시 400")
    void registerWithInvalidEmail() {
        given()
            .contentType("application/json")
            .body("{\"email\":\"not-an-email\",\"password\":\"pass1234\"}")
        .when()
            .post("/api/members/register")
        .then()
            .statusCode(400);
    }
}
