package org.breedinginsight.test;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.http.*;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.mockito.Mock;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;

/*
 * Integration tests of UserController endpoints using test database and mocked Micronaut authentication
 */

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserControllerTest {

    private String testUserUUID;

    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    public void getUserInfoRegisteredUser() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/bi/v1/userinfo").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals("Test User", result.get("name").getAsString(), "Wrong name");

    }

    @Test
    public void getUserInfoUnregisteredUser() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/bi/v1/userinfo").cookie(new NettyCookie("phylo-token", "test-unregistered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }

    @Test
    @Order(1)
    public void getUsersAllExisting() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/bi/v1/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonArray data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("data");

        // depends on db setup
        assertEquals(4, data.size(), "Wrong number users");
    }

    @Test
    public void getUsersExistingId() {

        // depends on db setup
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/bi/v1/users/74a6ebfe-d114-419b-8bdc-2f7b52d26172").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals("74a6ebfe-d114-419b-8bdc-2f7b52d26172", result.get("id").getAsString(), "Wrong id");
        assertEquals("Test User", result.get("name").getAsString(), "Wrong name");
        assertEquals("1111-2222-3333-4444", result.get("orcid").getAsString(), "Wrong orcid");
    }

    @Test
    public void getUsersNotExistingId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/bi/v1/users/e91f816b-b7aa-4444-9d00-3e25fdc00e15").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test void getUsersServerError() {
        MockDataProvider mockDataProvider = new MockDataProvider() {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) throws SQLException {
                throw new SQLException("TEST");
            }
        };

        Connection connection = new MockConnection(mockDataProvider);
        DSLContext create = DSL.using(connection, SQLDialect.POSTGRES);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/bi/v1/users/e91f816b-b7aa-4444-9d00-3e25fdc00e15").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    @Order(2)
    public void postUsersSuccess() {
        String name = "Test User2";
        String email = "test2@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/bi/v1/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        try {
            JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

            assertEquals(name, result.get("name").getAsString(), "Wrong name");
            assertEquals(email, result.get("email").getAsString(), "Wrong email");

            testUserUUID = result.get("id").getAsString();
            System.out.println("UUID = " + testUserUUID);

        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @Order(3)
    public void deleteUsersExisting() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/bi/v1/users/"+testUserUUID).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    public void deleteUsersNotExistingId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/bi/v1/users/e91f816b-b7aa-4e89-9d00-3e25fdc00e14").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void postUsersMissingEmail() {
        String name = "Test User2";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/bi/v1/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postUsersMissingName() {
        String email = "test@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/bi/v1/users", requestBody.toString())
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postUsersEmptyBody() {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/bi/v1/users", "")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postUsersEmailAlreadyExists() {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Test User");
        requestBody.addProperty("email", "test@test.com");

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/bi/v1/users", requestBody.toString())
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    public void putUsersSuccess() {

        String name = "Test User Edited";
        String email = "test_edited@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/bi/v1/users/" + testUserUUID, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        try {
            JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

            assertEquals(name, result.get("name").getAsString(), "Wrong name");
            assertEquals(email, result.get("email").getAsString(), "Wrong email");

        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }



}
