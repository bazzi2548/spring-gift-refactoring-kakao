package gift.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("상품 목록을 페이징 조회한다")
    void getProducts() {
        given()
            .param("page", 0)
            .param("size", 5)
        .when()
            .get("/api/products")
        .then()
            .statusCode(200)
            .body("content.size()", equalTo(5))
            .body("totalElements", greaterThanOrEqualTo(6));
    }

    @Test
    @DisplayName("상품을 단건 조회한다")
    void getProduct() {
        given()
        .when()
            .get("/api/products/1")
        .then()
            .statusCode(200)
            .body("name", equalTo("맥북 프로 16인치"));
    }

    @Test
    @DisplayName("상품을 생성한다")
    void createProduct() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"테스트상품\",\"price\":10000,\"imageUrl\":\"https://example.com/img.jpg\",\"categoryId\":1}")
        .when()
            .post("/api/products")
        .then()
            .statusCode(201)
            .body("name", equalTo("테스트상품"))
            .body("price", equalTo(10000))
            .body("categoryId", equalTo(1));
    }

    @Test
    @DisplayName("상품을 수정한다")
    void updateProduct() {
        Long id = createProductAndGetId("수정용상품", 5000, 1L);

        given()
            .contentType("application/json")
            .body("{\"name\":\"수정된상품\",\"price\":7000,\"imageUrl\":\"https://example.com/updated.jpg\",\"categoryId\":1}")
        .when()
            .put("/api/products/" + id)
        .then()
            .statusCode(200)
            .body("name", equalTo("수정된상품"))
            .body("price", equalTo(7000));
    }

    @Test
    @DisplayName("상품을 삭제한다")
    void deleteProduct() {
        Long id = createProductAndGetId("삭제용상품", 5000, 1L);

        given()
        .when()
            .delete("/api/products/" + id)
        .then()
            .statusCode(204);

        given()
        .when()
            .get("/api/products/" + id)
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 404")
    void getProductNotFound() {
        given()
        .when()
            .get("/api/products/999999")
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("카카오 포함 이름 생성 실패")
    void createProductWithInvalidName() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"카카오톡\",\"price\":10000,\"imageUrl\":\"https://example.com/img.jpg\",\"categoryId\":1}")
        .when()
            .post("/api/products")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 생성 실패")
    void createProductWithNonExistentCategory() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"테스트상품2\",\"price\":10000,\"imageUrl\":\"https://example.com/img.jpg\",\"categoryId\":999999}")
        .when()
            .post("/api/products")
        .then()
            .statusCode(404);
    }
}
