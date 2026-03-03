package gift.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptionAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("상품의 옵션 목록을 조회한다")
    void getOptions() {
        given()
        .when()
            .get("/api/products/1/options")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2));
    }

    @Test
    @DisplayName("옵션을 생성한다")
    void createOption() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"새로운 옵션\",\"quantity\":100}")
        .when()
            .post("/api/products/1/options")
        .then()
            .statusCode(201)
            .body("name", equalTo("새로운 옵션"))
            .body("quantity", equalTo(100));
    }

    @Test
    @DisplayName("옵션을 삭제한다")
    void deleteOption() {
        Long optionId = createOptionAndGetId(1L, "삭제용 옵션", 50);

        given()
        .when()
            .delete("/api/products/1/options/" + optionId)
        .then()
            .statusCode(204);
    }

    @Test
    @DisplayName("마지막 옵션은 삭제할 수 없다")
    void deleteLastOption() {
        Long categoryId = createCategoryAndGetId("옵션테스트카테고리");
        Long productId = createProductAndGetId("옵션테스트상품", 1000, categoryId);
        Long optionId = createOptionAndGetId(productId, "유일한 옵션", 10);

        given()
        .when()
            .delete("/api/products/" + productId + "/options/" + optionId)
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("동일 이름 옵션 추가 시 400")
    void createDuplicateOption() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"스페이스 블랙 / M1 Pro\",\"quantity\":10}")
        .when()
            .post("/api/products/1/options")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("존재하지 않는 상품 옵션 조회 시 404")
    void getOptionsProductNotFound() {
        given()
        .when()
            .get("/api/products/999999/options")
        .then()
            .statusCode(404);
    }
}
