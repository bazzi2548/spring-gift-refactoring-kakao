package gift.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WishAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("위시리스트를 조회한다")
    void getWishes() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/wishes")
        .then()
            .statusCode(200)
            .body("content", notNullValue());
    }

    @Test
    @DisplayName("위시리스트에 상품을 추가한다")
    void addWish() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"productId\":2}")
        .when()
            .post("/api/wishes")
        .then()
            .statusCode(201);
    }

    @Test
    @DisplayName("이미 있는 상품 추가 시 200")
    void addWishDuplicate() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"productId\":1}")
        .when()
            .post("/api/wishes")
        .then()
            .statusCode(200);
    }

    @Test
    @DisplayName("위시를 삭제한다")
    void removeWish() {
        String token = registerAndGetToken("wish-remove@example.com", "pass1234");

        Long wishId = given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"productId\":1}")
        .when()
            .post("/api/wishes")
        .then()
            .extract().jsonPath().getLong("id");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/api/wishes/" + wishId)
        .then()
            .statusCode(204);
    }

    @Test
    @DisplayName("다른 회원 위시 삭제 시 403")
    void removeWishForbidden() {
        String token1 = registerAndGetToken("wish-owner@example.com", "pass1234");
        String token2 = registerAndGetToken("wish-other@example.com", "pass1234");

        Long wishId = given()
            .header("Authorization", "Bearer " + token1)
            .contentType("application/json")
            .body("{\"productId\":4}")
        .when()
            .post("/api/wishes")
        .then()
            .extract().jsonPath().getLong("id");

        given()
            .header("Authorization", "Bearer " + token2)
        .when()
            .delete("/api/wishes/" + wishId)
        .then()
            .statusCode(403);
    }

    @Test
    @DisplayName("인증 없이 조회 시 401")
    void getWishesUnauthorized() {
        given()
            .header("Authorization", "Bearer invalid-token")
        .when()
            .get("/api/wishes")
        .then()
            .statusCode(401);
    }

    @Test
    @DisplayName("없는 상품 추가 시 404")
    void addWishProductNotFound() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"productId\":999999}")
        .when()
            .post("/api/wishes")
        .then()
            .statusCode(404);
    }
}
