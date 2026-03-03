package gift.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

class OrderAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("주문 목록을 조회한다")
    void getOrders() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/orders")
        .then()
            .statusCode(200)
            .body("content", notNullValue());
    }

    @Test
    @DisplayName("주문을 생성한다")
    void createOrder() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"optionId\":5,\"quantity\":1,\"message\":\"테스트 주문\"}")
        .when()
            .post("/api/orders")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }

    @Test
    @DisplayName("포인트 부족 시 400")
    void createOrderInsufficientPoints() {
        String token = registerAndGetToken("broke@example.com", "pass1234");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"optionId\":1,\"quantity\":1,\"message\":\"\"}")
        .when()
            .post("/api/orders")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("재고 초과 주문 시 400")
    void createOrderExceedingStock() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"optionId\":1,\"quantity\":99999999,\"message\":\"\"}")
        .when()
            .post("/api/orders")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("없는 옵션 주문 시 404")
    void createOrderOptionNotFound() {
        String token = loginAndGetToken("user1@example.com", "password1");

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body("{\"optionId\":999999,\"quantity\":1,\"message\":\"\"}")
        .when()
            .post("/api/orders")
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("인증 없이 조회 시 401")
    void getOrdersUnauthorized() {
        given()
            .header("Authorization", "Bearer invalid-token")
        .when()
            .get("/api/orders")
        .then()
            .statusCode(401);
    }
}
