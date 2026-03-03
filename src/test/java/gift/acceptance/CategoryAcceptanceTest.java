package gift.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("카테고리 목록을 조회한다")
    void getCategories() {
        given()
        .when()
            .get("/api/categories")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(3))
            .body("name", hasItems("전자기기", "패션", "식품"));
    }

    @Test
    @DisplayName("카테고리를 생성한다")
    void createCategory() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"도서\",\"color\":\"#8B4513\",\"imageUrl\":\"https://example.com/books.jpg\",\"description\":\"책과 잡지\"}")
        .when()
            .post("/api/categories")
        .then()
            .statusCode(201)
            .body("name", equalTo("도서"))
            .body("color", equalTo("#8B4513"))
            .body("imageUrl", equalTo("https://example.com/books.jpg"))
            .body("description", equalTo("책과 잡지"));
    }

    @Test
    @DisplayName("카테고리를 수정한다")
    void updateCategory() {
        Long id = createCategoryAndGetId("수정용카테고리");

        given()
            .contentType("application/json")
            .body("{\"name\":\"수정된카테고리\",\"color\":\"#FF0000\",\"imageUrl\":\"https://example.com/updated.jpg\",\"description\":\"수정됨\"}")
        .when()
            .put("/api/categories/" + id)
        .then()
            .statusCode(200)
            .body("name", equalTo("수정된카테고리"))
            .body("color", equalTo("#FF0000"));
    }

    @Test
    @DisplayName("카테고리를 삭제한다")
    void deleteCategory() {
        Long id = createCategoryAndGetId("삭제용카테고리");

        given()
        .when()
            .delete("/api/categories/" + id)
        .then()
            .statusCode(204);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 수정 시 404")
    void updateCategoryNotFound() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"없는카테고리\",\"color\":\"#000000\",\"imageUrl\":\"https://example.com/img.jpg\",\"description\":\"\"}")
        .when()
            .put("/api/categories/999999")
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("이름이 비어있으면 카테고리 생성 실패")
    void createCategoryWithBlankName() {
        given()
            .contentType("application/json")
            .body("{\"name\":\"\",\"color\":\"#000000\",\"imageUrl\":\"https://example.com/img.jpg\",\"description\":\"\"}")
        .when()
            .post("/api/categories")
        .then()
            .statusCode(400);
    }
}
