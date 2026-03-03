package gift.acceptance;

import static io.restassured.RestAssured.given;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;

@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class AcceptanceTestBase {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    String registerAndGetToken(String email, String password) {
        return given()
            .contentType("application/json")
            .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post("/api/members/register")
        .then()
            .statusCode(201)
            .extract().jsonPath().getString("token");
    }

    String loginAndGetToken(String email, String password) {
        return given()
            .contentType("application/json")
            .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post("/api/members/login")
        .then()
            .statusCode(200)
            .extract().jsonPath().getString("token");
    }

    Long createCategoryAndGetId(String name) {
        return given()
            .contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"color\":\"#000000\",\"imageUrl\":\"https://example.com/img.jpg\",\"description\":\"desc\"}")
        .when()
            .post("/api/categories")
        .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    Long createProductAndGetId(String name, int price, Long categoryId) {
        return given()
            .contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"price\":" + price + ",\"imageUrl\":\"https://example.com/img.jpg\",\"categoryId\":" + categoryId + "}")
        .when()
            .post("/api/products")
        .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    Long createOptionAndGetId(Long productId, String name, int quantity) {
        return given()
            .contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"quantity\":" + quantity + "}")
        .when()
            .post("/api/products/" + productId + "/options")
        .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");
    }
}
